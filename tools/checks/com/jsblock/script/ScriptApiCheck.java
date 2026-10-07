package com.jsblock.script;

import com.jsblock.data.PIDSPreset;
import mtr.data.ScheduleEntry;
import net.minecraft.core.BlockPos;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs a real JCM 2.x PIDS script through the real wrappers, without a game.
 *
 * <p>The engine's drawing is the part that cannot be exercised headlessly, so
 * {@link ScriptRenderContext#dryRun} records what a script asked to draw instead of rendering
 * it. Everything before that point — Rhino compilation, {@code include}, the globals, and the
 * whole {@code Text}/{@code Texture}/{@code Rectangle} builder chain — runs for real, so a
 * wrapper method this port is missing fails here exactly as it would in game.</p>
 *
 * <p>{@code include()} is overridden to read from ordinary directories, because the engine's
 * own implementation goes through Minecraft's resource manager.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * java com.jsblock.script.ScriptApiCheck &lt;script.js&gt; [resourceRoot ...]
 * </pre>
 * A resource root is a directory containing {@code assets/&lt;namespace&gt;/...}.
 */
public final class ScriptApiCheck {

	/** Searched in order when a script calls include(<namespace>:<path>). */
	private static final List<Path> RESOURCE_ROOTS = new ArrayList<>();

	private ScriptApiCheck() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("usage: ScriptApiCheck <script.js> [resourceRoot ...] [--arrivals=N] [--iterations=N]");
			System.exit(2);
		}
		String scriptArg = null;
		int arrivalCount = 2;
		int iterations = 1;
		for (String arg : args) {
			if (arg.startsWith("--arrivals=")) {
				arrivalCount = Integer.parseInt(arg.substring("--arrivals=".length()));
			} else if (arg.startsWith("--iterations=")) {
				iterations = Integer.parseInt(arg.substring("--iterations=".length()));
			} else if (scriptArg == null) {
				scriptArg = arg;
			} else {
				RESOURCE_ROOTS.add(Paths.get(arg));
			}
		}
		final Path script = Paths.get(scriptArg);
		if (!Files.isRegularFile(script)) {
			System.out.println("FAIL script not found: " + script);
			System.exit(2);
		}

		System.out.println("== script: " + script);
		System.out.println("   arrivals simulated: " + arrivalCount + ", render iterations: " + iterations);
		System.out.println("   resource roots: " + RESOURCE_ROOTS);

		final Context cx = Context.enter();
		int failures = 0;
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			installFileInclude(cx, scope);

			final String source = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
			cx.evaluateString(scope, source, script.getFileName().toString(), 1, null);
			System.out.println("OK   compiled (" + source.length() + " chars)");

			// create(ctx, state, pids) must exist and must not throw.
			final ScriptableObject state = new ScriptableObject() {
				@Override
				public String getClassName() {
					return "PIDSState";
				}
			};
			state.setPrototype(ScriptableObject.getObjectPrototype(scope));
			state.setParentScope(scope);

			final PIDSWrapper pids = stubPids(arrivalCount);
			System.out.println("OK   stub pids: " + pids.width + "x" + pids.height
					+ " type=" + pids.type + " arrivals=" + pids.arrivals().size());

			failures += callLifecycle(cx, scope, "create", state, pids);

			// render(ctx, state, pids) with a recording context, repeated to catch state drift.
			List<String> recorded = Collections.emptyList();
			for (int i = 0; i < iterations; i++) {
				final ScriptRenderContext ctx = ScriptRenderContext.dryRun(pids.width, pids.height, 1F);
				failures += callRender(cx, scope, state, pids, ctx, i);
				recorded = ctx.recordedCalls();
			}
			System.out.println("     draw calls recorded: " + recorded.size());
			for (String call : recorded) {
				System.out.println("       " + call);
			}
			if (recorded.isEmpty()) {
				System.out.println("FAIL render() produced no draw calls");
				failures++;
			} else {
				System.out.println("OK   render() produced draw calls");
			}
			failures += checkDrawColoursAreOpaque(recorded);

			failures += callLifecycle(cx, scope, "dispose", state, pids);
		} catch (Exception e) {
			System.out.println("FAIL " + e);
			e.printStackTrace(System.out);
			failures++;
		} finally {
			Context.exit();
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: SCRIPT OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/**
	 * Asserts that no script texture or rectangle is drawn fully transparent.
	 *
	 * <p>A preset writes an RGB colour — {@code .color(0x009944)} — and MTR 3's
	 * {@code IDrawing.drawTexture} reads the alpha straight out of it, so a value with no alpha
	 * channel draws nothing at all. That is invisible on a screenshot and cannot be reasoned
	 * about from a preset, so it is pinned here instead: every {@code Texture} and
	 * {@code Rectangle} in the recorded frame must carry alpha {@code FF}.</p>
	 *
	 * @return the number of failures
	 */
	private static int checkDrawColoursAreOpaque(List<String> recorded) {
		final java.util.regex.Pattern pattern =
				java.util.regex.Pattern.compile("^(Texture|Rectangle)\\(.*color=([0-9A-Fa-f]{8})\\)");
		int checked = 0;
		int failures = 0;
		for (String call : recorded) {
			final java.util.regex.Matcher matcher = pattern.matcher(call);
			if (!matcher.find()) {
				continue;
			}
			checked++;
			final int color = (int) Long.parseLong(matcher.group(2), 16);
			if ((color >>> 24) != 0xFF) {
				System.out.println("FAIL " + matcher.group(1) + " is drawn with alpha "
						+ String.format("%02X", color >>> 24) + ", so nothing is drawn: " + call);
				failures++;
			}
		}
		if (checked == 0) {
			/* A text-only preset -- the built-in pids_1a.js is one -- has nothing to check
			   here; the presets that do draw textures carry this assertion. */
			System.out.println("OK   no Texture/Rectangle call to check in this preset");
			return 0;
		}
		System.out.println(failures == 0
				? "OK   all " + checked + " texture/rectangle colours are opaque"
				: "FAIL " + failures + " of " + checked + " colours are transparent");
		return failures;
	}

	/**
	 * @param count how many upcoming trains to simulate. JCM 2.x's {@code arrivals().get(i)}
	 *              returns null past the end, which is exactly the case a preset must survive
	 *              on a platform with no trains -- so 0 is a case worth running.
	 */
	private static PIDSWrapper stubPids(int count) {
		final long now = System.currentTimeMillis();
		final List<ScheduleEntry> schedule = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			schedule.add(new ScheduleEntry(now + 90_000L * (i + 1), 6 - i, 1L + i, 2 + i));
		}
		return new PIDSWrapper("crt_pids", 3, 128, 72, new BlockPos(0, 64, 0),
				Collections.<Long>emptyList(),
				new String[]{"欢迎乘坐重庆轨道交通", "", ""},
				new boolean[]{false, false, false},
				schedule);
	}

	private static int callLifecycle(Context cx, Scriptable scope, String name, ScriptableObject state, PIDSWrapper pids) {
		final Object fn = scope.get(name, scope);
		if (!(fn instanceof Function)) {
			System.out.println("     (no " + name + "() defined)");
			return 0;
		}
		try {
			((Function) fn).call(cx, scope, scope, new Object[]{null, state, pids});
			System.out.println("OK   " + name + "()");
			return 0;
		} catch (Exception e) {
			System.out.println("FAIL " + name + "() threw: " + e);
			return 1;
		}
	}

	private static int callRender(Context cx, Scriptable scope, ScriptableObject state,
								  PIDSWrapper pids, ScriptRenderContext ctx, int iteration) {
		final Object fn = scope.get("render", scope);
		if (!(fn instanceof Function)) {
			System.out.println("FAIL no render() defined");
			return 1;
		}
		try {
			((Function) fn).call(cx, scope, scope, new Object[]{ctx, state, pids});
			if (iteration == 0) {
				System.out.println("OK   render() ran");
			}
			return 0;
		} catch (Exception e) {
			System.out.println("FAIL render() threw on iteration " + iteration + ": " + e);
			return 1;
		}
	}

	/**
	 * Replaces the engine's {@code include} with one that reads ordinary files, so a script's
	 * dependencies can be resolved outside the game.
	 */
	private static void installFileInclude(final Context cx, final Scriptable scope) {
		ScriptableObject.putProperty(scope, "include", new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
				if (args.length == 0 || args[0] == null) {
					return Undefined.instance;
				}
				final String reference = Context.toString(args[0]);
				final int colon = reference.indexOf(':');
				final String namespace = colon < 0 ? "minecraft" : reference.substring(0, colon);
				final String path = colon < 0 ? reference : reference.substring(colon + 1);

				for (Path root : RESOURCE_ROOTS) {
					final Path candidate = root.resolve("assets").resolve(namespace).resolve(path);
					if (Files.isRegularFile(candidate)) {
						try {
							final String text = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
							context.evaluateString(s, text, candidate.toString(), 1, null);
							System.out.println("     included " + reference + " -> " + candidate.getFileName());
							return Undefined.instance;
						} catch (Exception e) {
							System.out.println("FAIL include " + reference + " threw: " + e);
							return Undefined.instance;
						}
					}
				}
				System.out.println("     include miss: " + reference + " (not found in any resource root)");
				return Undefined.instance;
			}
		});
	}

	/** Resolves a resource root from the command line, tolerating a trailing separator. */
	@SuppressWarnings("unused")
	private static Path root(String value) {
		return new File(value).toPath();
	}
}
