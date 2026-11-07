package com.jsblock.script;

import mtr.data.ScheduleEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks the asynchronous execution model: that recording and replaying a frame is
 * indistinguishable from running the script inline, that a replayed frame does not depend on the
 * script's {@code state}, and that the thread machinery behaves (no throw onto the caller, a slow
 * script does not block the submitter, the first frame runs synchronously exactly once).
 *
 * <p>Everything here runs without a game. The "inline" side uses {@link ScriptRenderContext#dryRun},
 * which is the established headless representation of what a script asked to draw; the asynchronous
 * side uses {@link ScriptRenderContext#recording} on the script thread and replays the captured
 * {@link RecordedCall}s into a second dry-run context. Comparing the two lists is the consistency
 * proof for the recorder/replayer; no GPU is involved on either side.</p>
 *
 * <h2>Usage</h2>
 * <pre>java com.jsblock.script.ScriptAsyncCheck</pre>
 */
public final class ScriptAsyncCheck {

	private static final BlockPos PANEL_POS = new BlockPos(12, 65, -34);
	private static final int WIDTH = 128;
	private static final int HEIGHT = 72;

	private static int failures;

	private ScriptAsyncCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== asynchronous script execution (ScriptAsyncCheck) ==");
		try {
			checkRecordingRoundTrip();
			checkReplayDoesNotReadState();
			checkExceptionKeepsLastGoodFrame();
			checkSlowScriptDoesNotBlockSubmit();
			checkFirstFrameRunsOnce();
			checkFrameStatsSample();
		} catch (Throwable t) {
			System.out.println("FAIL unexpected: " + t);
			t.printStackTrace(System.out);
			failures++;
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: ASYNC CHECK OK"
				: "RESULT: " + failures + " ASYNC FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------
	// 1. inline versus record + replay
	// ------------------------------------------------------------------

	/**
	 * The same script, run inline into a dry-run context and run through record + replay, must
	 * produce the same call sequence — descriptions and full signatures, in order.
	 */
	private static void checkRecordingRoundTrip() throws Exception {
		final String source = ""
				+ "function create(ctx, state, pids) {\n"
				+ "  state.label = 'Alpha';\n"
				+ "  state.color = 0x123456;\n"
				+ "}\n"
				+ "function render(ctx, state, pids) {\n"
				+ "  Rectangle.create('row').color(0x112233).pos(1, 2).size(30, 4).draw(ctx);\n"
				+ "  Texture.create('img').texture('mtr:textures/block/white.png')\n"
				+ "      .uv(0.1, 0.2, 0.9, 0.8).color(0x223344).naturalLight()\n"
				+ "      .pos(2.5, 3.5).size(4, 5).draw(ctx);\n"
				+ "  var m = new Matrices();\n"
				+ "  m.translate(3, 4, 0);\n"
				+ "  m.rotateZDegrees(15);\n"
				+ "  Text.create('label').text(state.label + ' / ' + pids.stationName())\n"
				+ "      .font('mtr:mtr').scale(0.75).bold().italic().shadowed().lineHeight(1.2)\n"
				+ "      .wrapText().rightAlign().color(state.color).matrices(m)\n"
				+ "      .pos(4, 5).size(40, 10).draw(ctx);\n"
				+ "  var view = ctx.parseComponent('{\"component\":\"clock\",\"x\":6,\"y\":7,\"width\":20,\"height\":8}');\n"
				+ "  ctx.draw(view);\n"
				+ "}\n";

		final PIDSWrapper pids = stubPids(2);
		final ScriptRenderContext inline = ScriptRenderContext.dryRun(WIDTH, HEIGHT, 1F);
		final ScriptEngine.Program inlineProgram = compile(source, "async-inline");
		inlineProgram.start(pids);
		if (!inlineProgram.renderRecovering(inline, pids)) {
			fail("inline render failed: " + inlineProgram.getLastError());
			return;
		}

		final ScriptEngine.Program asyncProgram = compile(source, "async-record");
		final ScriptRenderContext recording = recordingContext();
		asyncProgram.start(pids);
		if (!asyncProgram.renderRecovering(recording, pids)) {
			fail("recording render failed: " + asyncProgram.getLastError());
			return;
		}
		final List<RecordedCall> recorded = recording.recordedCallList();
		if (recorded.isEmpty()) {
			fail("recording produced no calls");
			return;
		}

		final ScriptRenderContext replay = ScriptRenderContext.dryRun(WIDTH, HEIGHT, 1F);
		replay.replay(recorded);
		final List<String> inlineDescriptions = inline.recordedCalls();
		final List<String> replayDescriptions = replay.recordedCalls();
		final List<RecordedCall> replayed = replay.recordedCallList();

		System.out.println("     inline calls=" + inlineDescriptions.size()
				+ ", recorded=" + recorded.size() + ", replayed=" + replayDescriptions.size());
		if (inlineDescriptions.size() != replayDescriptions.size()) {
			fail("call count differs: inline=" + inlineDescriptions.size()
					+ " replay=" + replayDescriptions.size());
			return;
		}
		for (int i = 0; i < inlineDescriptions.size(); i++) {
			if (!inlineDescriptions.get(i).equals(replayDescriptions.get(i))) {
				fail("description differs at " + i + "\n  inline: " + inlineDescriptions.get(i)
						+ "\n  replay: " + replayDescriptions.get(i));
				return;
			}
			if (!recorded.get(i).signature().equals(replayed.get(i).signature())) {
				fail("signature differs at " + i + "\n  record: " + recorded.get(i).signature()
						+ "\n  replay: " + replayed.get(i).signature());
				return;
			}
		}
		System.out.println("OK   inline and record+replay produce the same "
				+ inlineDescriptions.size() + "-call sequence (descriptions and signatures)");
		for (int i = 0; i < inlineDescriptions.size(); i++) {
			System.out.println("       " + i + ": " + inlineDescriptions.get(i));
		}
	}

	// ------------------------------------------------------------------
	// 2. replay is a value, not a view of state
	// ------------------------------------------------------------------

	/** A captured frame must survive the script state being changed or cleared afterwards. */
	private static void checkReplayDoesNotReadState() throws Exception {
		final String source = ""
				+ "function create(ctx, state, pids) { state.label = 'before'; state.color = 0x0A0B0C; }\n"
				+ "function render(ctx, state, pids) {\n"
				+ "  Text.create('t').text(state.label).color(state.color).pos(0, 0).size(50, 10).draw(ctx);\n"
				+ "}\n";
		final PIDSWrapper pids = stubPids(0);
		final ScriptEngine.Program program = compile(source, "async-state");
		final ScriptRenderContext recording = recordingContext();
		program.start(pids);
		if (!program.renderRecovering(recording, pids)) {
			fail("state test render failed: " + program.getLastError());
			return;
		}
		final List<RecordedCall> recorded = recording.recordedCallList();
		final String before = recorded.get(0).signature();

		/* Change the state the script drew from, then drop the program's reference to it. The
		   record must not follow: it captured the values, not the object. */
		final Scriptable state = program.getState();
		state.put("label", state, "after");
		state.put("color", state, 0xFFFFFF);
		program.dispose();

		final ScriptRenderContext replay = ScriptRenderContext.dryRun(WIDTH, HEIGHT, 1F);
		replay.replay(recorded);
		final String after = replay.recordedCallList().get(0).signature();
		if (!before.equals(after)) {
			fail("replay changed with state:\n  before: " + before + "\n  after:  " + after);
			return;
		}
		if (!replay.recordedCalls().get(0).contains("before")) {
			fail("replayed description lost the recorded text: " + replay.recordedCalls().get(0));
			return;
		}
		System.out.println("OK   replay does not read script state: " + after);
	}

	// ------------------------------------------------------------------
	// 3. a throw keeps the last good frame and never reaches the caller
	// ------------------------------------------------------------------

	private static void checkExceptionKeepsLastGoodFrame() throws Exception {
		final String source = ""
				+ "function create(ctx, state, pids) { state.fail = false; }\n"
				+ "function render(ctx, state, pids) {\n"
				+ "  if (state.fail) throw new Error('boom');\n"
				+ "  Rectangle.create('good').color(0x00FF00).pos(1, 1).size(9, 9).draw(ctx);\n"
				+ "}\n";
		final PIDSWrapper pids = stubPids(0);
		final ScriptEngine.Program program = compile(source, "async-throw");
		final ScriptInstance instance = new ScriptInstance(program);

		final ScriptRenderContext first = recordingContext();
		if (!instance.runFirstFrame(first, pids, PANEL_POS)) {
			fail("first frame did not run");
			return;
		}
		if (instance.lastCalls(PANEL_POS).size() != 1 || instance.lastError() != null) {
			fail("first frame did not produce one good call: calls=" + instance.lastCalls(PANEL_POS).size()
					+ " error=" + instance.lastError());
			return;
		}
		final String good = instance.lastCalls(PANEL_POS).get(0).signature();

		final Scriptable state = program.getState();
		state.put("fail", state, Boolean.TRUE);
		if (!instance.submitRender(recordingContext(), pids, PANEL_POS)) {
			fail("failing pass was not submitted");
			return;
		}
		if (!instance.awaitIdle(10_000L)) {
			fail("failing pass did not finish");
			return;
		}
		if (instance.lastError() == null || !instance.lastError().contains("boom")) {
			fail("lastError was not recorded: " + instance.lastError());
			return;
		}
		if (instance.lastCalls(PANEL_POS).size() != 1
				|| !good.equals(instance.lastCalls(PANEL_POS).get(0).signature())) {
			fail("the last good frame was not kept: calls=" + instance.lastCalls(PANEL_POS).size());
			return;
		}
		System.out.println("OK   throw kept the previous frame and was reported as: " + instance.lastError());
	}

	// ------------------------------------------------------------------
	// 4. submission is non-blocking
	// ------------------------------------------------------------------

	private static void checkSlowScriptDoesNotBlockSubmit() throws Exception {
		final String source = ""
				+ "function render(ctx, state, pids) {\n"
				+ "  var until = Date.now() + 50;\n"
				+ "  while (Date.now() < until) { }\n"
				+ "  Rectangle.create('slow').color(0x0000FF).pos(0, 0).size(5, 5).draw(ctx);\n"
				+ "}\n";
		final PIDSWrapper pids = stubPids(0);
		final ScriptEngine.Program program = compile(source, "async-slow");
		final ScriptInstance instance = new ScriptInstance(program);

		/* Warm the executor and JIT with one full pass; the assertion is about a warm submit. */
		instance.runFirstFrame(recordingContext(), pids, PANEL_POS);
		if (!instance.submitRender(recordingContext(), pids, PANEL_POS)) {
			fail("warm-up pass was not submitted");
			return;
		}
		if (!instance.awaitIdle(10_000L)) {
			fail("warm-up pass did not finish");
			return;
		}

		final long start = System.nanoTime();
		final boolean submitted = instance.submitRender(recordingContext(), pids, PANEL_POS);
		final double submitMs = (System.nanoTime() - start) / 1_000_000.0;
		if (!submitted) {
			fail("slow pass was not submitted");
			return;
		}
		if (submitMs >= 5.0) {
			fail(String.format(java.util.Locale.ROOT,
					"submitRender() blocked for %.3f ms (must be < 5 ms)", submitMs));
			return;
		}
		/* While it runs, a second submit must be refused rather than queued. */
		if (instance.submitRender(recordingContext(), pids, PANEL_POS)) {
			fail("a second pass was accepted while one was running");
			return;
		}
		if (!instance.awaitIdle(10_000L)) {
			fail("slow pass did not finish");
			return;
		}
		if (instance.lastCalls(PANEL_POS).isEmpty()) {
			fail("slow pass produced no calls");
			return;
		}
		System.out.println(String.format(java.util.Locale.ROOT,
				"OK   submit returned in %.3f ms while render() busy-waited 50 ms;"
						+ " a concurrent submit was refused", submitMs));
	}

	// ------------------------------------------------------------------
	// 5. the synchronous first frame happens once
	// ------------------------------------------------------------------

	private static void checkFirstFrameRunsOnce() throws Exception {
		final String source = ""
				+ "function create(ctx, state, pids) { state.n = 0; }\n"
				+ "function render(ctx, state, pids) {\n"
				+ "  state.n++;\n"
				+ "  Rectangle.create('r').color(0xFFFFFF).pos(0, 0).size(4, 4).draw(ctx);\n"
				+ "}\n";
		final PIDSWrapper pids = stubPids(0);
		final ScriptInstance instance = new ScriptInstance(compile(source, "async-first"));

		if (!instance.runFirstFrame(recordingContext(), pids, PANEL_POS)) {
			fail("first frame did not run");
			return;
		}
		if (instance.runFirstFrame(recordingContext(), pids, PANEL_POS)) {
			fail("first frame ran a second time");
			return;
		}
		if (instance.firstFrameRuns(PANEL_POS) != 1) {
			fail("firstFrameRuns=" + instance.firstFrameRuns(PANEL_POS) + ", expected 1");
			return;
		}
		if (!instance.submitRender(recordingContext(), pids, PANEL_POS)) {
			fail("post-first-frame pass was not submitted");
			return;
		}
		if (!instance.awaitIdle(10_000L)) {
			fail("post-first-frame pass did not finish");
			return;
		}
		if (instance.firstFrameRuns(PANEL_POS) != 1) {
			fail("submission re-ran the first frame: " + instance.firstFrameRuns(PANEL_POS));
			return;
		}
		System.out.println("OK   the synchronous first frame ran exactly once");
	}

	// ------------------------------------------------------------------
	// 6. the frame statistics line, when enabled
	// ------------------------------------------------------------------

	/**
	 * Emits a {@code PidsFrameStats} sample line with {@code async=on} and the replay counters.
	 *
	 * <p>Only with {@code -Djsblock.pids.canvasStats=true}. The check opens real frames, replays a
	 * recorded list into a dry-run context (so section C is measured), and lets the one-second
	 * reporting window elapse; the resulting line is the same format a client logs.</p>
	 */
	private static void checkFrameStatsSample() throws Exception {
		if (!PidsFrameStats.ENABLED) {
			return;
		}
		final String source = ""
				+ "function render(ctx, state, pids) {\n"
				+ "  Rectangle.create('bg').color(0x101010).pos(0, 0).size(128, 72).draw(ctx);\n"
				+ "}\n";
		final PIDSWrapper pids = stubPids(0);
		final ScriptEngine.Program program = compile(source, "async-stats");
		final ScriptRenderContext recording = recordingContext();
		program.start(pids);
		program.renderRecovering(recording, pids);
		final List<RecordedCall> calls = recording.recordedCallList();

		final long windowStart = System.nanoTime();
		int frames = 0;
		while (System.nanoTime() - windowStart < 1_100_000_000L) {
			final long frameStart = PidsFrameStats.frameStart();
			PidsFrameStats.begin(frameStart, "async-check", PANEL_POS);
			PidsFrameStats.endSectionA();
			PidsFrameStats.noteAsyncSubmit();
			final long replayStart = PidsFrameStats.mark();
			final ScriptRenderContext replay = ScriptRenderContext.dryRun(WIDTH, HEIGHT, 1F);
			replay.replay(calls);
			PidsFrameStats.noteReplayCalls(calls.size());
			PidsFrameStats.addSectionB(replayStart);
			PidsFrameStats.end();
			frames++;
			Thread.sleep(2L);
		}
		System.out.println("OK   PidsFrameStats sample emitted over " + frames
				+ " frames (async=on, submit=1, replay=" + calls.size() + ")");
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static ScriptEngine.Program compile(String source, String name) {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			cx.evaluateString(scope, source, name + ".js", 1, null);
			return new ScriptEngine.Program(name, name, name, PANEL_POS, scope);
		} finally {
			Context.exit();
		}
	}

	private static ScriptRenderContext recordingContext() {
		final com.jsblock.pids.PIDSContext context = new com.jsblock.pids.PIDSContext(
				null, PANEL_POS, Direction.NORTH, new String[0],
				Collections.emptyList(), Collections.emptyList(), new boolean[0], 0D, 0L);
		return ScriptRenderContext.recording(WIDTH, HEIGHT, 1F, Direction.NORTH,
				mtr.data.IGui.MAX_LIGHT_GLOWING, context);
	}

	private static PIDSWrapper stubPids(int count) {
		final long now = System.currentTimeMillis();
		final List<ScheduleEntry> schedule = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			schedule.add(new ScheduleEntry(now + 90_000L * (i + 1), 6 - i, 1L + i, 2 + i));
		}
		return new PIDSWrapper("crt_pids", 3, WIDTH, HEIGHT, PANEL_POS,
				Collections.<Long>emptyList(),
				new String[]{"one", "two", "three"},
				new boolean[]{false, false, false},
				schedule);
	}

	private static void fail(String message) {
		System.out.println("FAIL " + message);
		failures++;
	}
}
