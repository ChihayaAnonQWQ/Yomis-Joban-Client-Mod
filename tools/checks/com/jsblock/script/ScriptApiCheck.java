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
			failures += checkClientDataGlobals(cx, scope);
			failures += checkParseComponent(cx, scope);
			failures += checkArrivalsContract(pids);
			failures += checkLenientRetryRenders(cx, scope, state, pids);

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
	 * Pins down the two client-data globals, which are easy to conflate and were.
	 *
	 * <p>JCM 2.x installs {@code MinecraftClient} globally and {@code MTRClientData} for the PIDS
	 * scope, and reads two <em>different</em> classes for them: its bytecode loads
	 * {@code MinecraftClientUtil} for the first and {@code mtr/client/ClientData} for the second.
	 * A port that treats the second as a second name for the first therefore compiles, loads, and
	 * leaves every {@code MTRClientData.STATIONS}-style read returning {@code undefined} -- a pack
	 * that draws nothing and reports nothing.</p>
	 *
	 * <p>The second half is the sandbox: {@code mtr.*} is not in
	 * {@link ScriptClassShutter}'s allow-list, so this is also the place that says whether a class
	 * handed to the scope directly can still be used. A global that the shutter silently empties
	 * would be the same failure by another route.</p>
	 *
	 * @return the number of failures
	 */
	private static int checkClientDataGlobals(Context cx, Scriptable scope) {
		int failures = 0;

		final Object minecraftClient = scope.get("MinecraftClient", scope);
		final Object mtrClientData = scope.get("MTRClientData", scope);

		if (!(minecraftClient instanceof org.mozilla.javascript.NativeJavaClass)) {
			System.out.println("FAIL MinecraftClient is missing from the scope");
			failures++;
		} else if (((org.mozilla.javascript.NativeJavaClass) minecraftClient).getClassObject()
				!= ScriptEngine.MinecraftClient.class) {
			System.out.println("FAIL MinecraftClient resolves to "
					+ ((org.mozilla.javascript.NativeJavaClass) minecraftClient).getClassObject().getName()
					+ " instead of the Minecraft client helper");
			failures++;
		} else {
			System.out.println("OK   MinecraftClient -> the game-state helper");
		}

		if (!(mtrClientData instanceof org.mozilla.javascript.NativeJavaClass)) {
			System.out.println("FAIL MTRClientData is missing from the scope");
			failures++;
		} else if (((org.mozilla.javascript.NativeJavaClass) mtrClientData).getClassObject()
				!= mtr.client.ClientData.class) {
			System.out.println("FAIL MTRClientData resolves to "
					+ ((org.mozilla.javascript.NativeJavaClass) mtrClientData).getClassObject().getName()
					+ " instead of mtr.client.ClientData, which is what JCM 2.x wires it to");
			failures++;
		} else {
			System.out.println("OK   MTRClientData -> mtr.client.ClientData, as JCM 2.x has it"
					+ " (and not a second name for MinecraftClient)");
		}

		/* Reachable through the shutter, which is the half that decides whether it is usable. */
		for (String[] probe : new String[][]{
				{"MTRClientData.STATIONS", "the station map the built-in PIDS read"},
				{"MTRClientData.PLATFORMS", "the platform map"},
				{"MTRClientData.SCHEDULES_FOR_PLATFORM", "the arrival lists"}}) {
			try {
				final Object value = cx.evaluateString(scope, probe[0], "client-data-check", 1, null);
				if (value == null || value == Undefined.instance) {
					System.out.println("FAIL " + probe[0] + " (" + probe[1] + ") is not readable");
					failures++;
				} else {
					System.out.println("OK   " + probe[0] + " is readable (" + probe[1] + ")");
				}
			} catch (Exception e) {
				System.out.println("FAIL " + probe[0] + " (" + probe[1] + ") threw: " + e);
				failures++;
			}
		}
		return failures;
	}

	/**
	 * Runs {@code ctx.parseComponent(...)} from inside a script, through the real scope.
	 *
	 * <p>JCM 2.x's only entry point into its declarative components is that one call, and the
	 * declaration it takes — {@code component}, {@code x}, {@code y}, {@code width}, {@code height},
	 * plus the component's own options — is the same JSON a preset's {@code components} array holds.
	 * This is the one place the two spellings can be compared, so it checks the whole path: the parse,
	 * the rectangle the component reports, the {@code canRender} rule, and that drawing it reaches the
	 * frame's draw-call list like any other element.</p>
	 *
	 * <p>The clock is the case the documentation's own examples use and the one that needs no panel
	 * data, which is why it is here rather than an arrival component: on a headless context there are
	 * no trains to draw, and a check whose fixture has to invent them tests the fixture.</p>
	 *
	 * <p>Refusals are half the API: a script author mistypes a component name far more often than they
	 * get it right, and {@code null} would have them hunting for "cannot call method of null" in a
	 * panel that never appears.</p>
	 *
	 * @return the number of failures
	 */
	private static int checkParseComponent(Context cx, Scriptable scope) {
		int failures = 0;
		final ScriptRenderContext ctx = ScriptRenderContext.dryRun(136, 76, 1F);
		/* The script side of the check: the context is handed to the scope the way the engine hands it
		   to a preset's render(), so every call below is resolved through Rhino and the sandbox. */
		ScriptableObject.putProperty(scope, "PROBE_CTX", ctx);

		final int before = ctx.recordedCalls().size();

		/* A clock, parsed and drawn exactly as the guide's example writes it. */
		final Object type = evaluate(cx, scope,
				"var c = PROBE_CTX.parseComponent('{\"component\":\"clock\",\"x\":4,\"y\":2,\"width\":40,\"height\":10,\"format\":\"HH:mm\"}');"
						+ " String(c.type());");
		if ("clock".equals(String.valueOf(type))) {
			System.out.println("OK   ctx.parseComponent() returns a clock component");
		} else {
			System.out.println("FAIL ctx.parseComponent() returned " + type + " instead of a clock component");
			failures++;
		}

		final Object box = evaluate(cx, scope, "[c.x(), c.y(), c.width(), c.height()].join(\",\")");
		if ("4,2,40,10".equals(String.valueOf(box))) {
			System.out.println("OK   the component reports the rectangle it was declared with: " + box);
		} else {
			System.out.println("FAIL the component reports " + box + " instead of 4,2,40,10");
			failures++;
		}

		/* The JSON path's own guard, asked from the script. No world here, so a clock is not drawn --
		   which is the same answer a components-array preset would get on a panel with no data. */
		final Object canRender = evaluate(cx, scope, "c.canRender()");
		if (Boolean.FALSE.equals(canRender)) {
			System.out.println("OK   canRender() applies the component's own rule (false without a world)");
		} else {
			System.out.println("FAIL canRender() returned " + canRender + " for a clock with no world");
			failures++;
		}

		evaluate(cx, scope, "c.render(PROBE_CTX);");
		if (ctx.recordedCalls().size() == before + 1
				&& ctx.recordedCalls().get(before).startsWith("Component(type=clock")) {
			System.out.println("OK   component.render(ctx) reaches the frame: " + ctx.recordedCalls().get(before));
		} else {
			System.out.println("FAIL component.render(ctx) recorded " + ctx.recordedCalls().size()
					+ " calls (expected " + (before + 1) + ")");
			failures++;
		}

		/* The spelling a script author reaches for next, and the one v2's own draw() takes: an
		   Object. Both must land in the same place. */
		evaluate(cx, scope, "PROBE_CTX.draw(c);");
		if (ctx.recordedCalls().size() == before + 2) {
			System.out.println("OK   ctx.draw(component) draws it too");
		} else {
			System.out.println("FAIL ctx.draw(component) recorded " + ctx.recordedCalls().size()
					+ " calls (expected " + (before + 2) + ")");
			failures++;
		}

		/* A second, unrelated type, so the registry is what is being exercised and not one hard-coded
		   branch. custom_text draws without any panel data at all. */
		final Object textType = evaluate(cx, scope,
				"String(PROBE_CTX.parseComponent('{\"component\":\"custom_text\",\"text\":\"Hello\",\"x\":0,\"y\":0,\"width\":30,\"height\":8}').type())");
		if ("custom_text".equals(String.valueOf(textType))) {
			System.out.println("OK   every registered type is reachable, not just the one: custom_text parses");
		} else {
			System.out.println("FAIL custom_text parsed as " + textType);
			failures++;
		}

		/* And the refusals, which must name the problem. */
		failures += expectRefused(cx, scope, "PROBE_CTX.parseComponent('this is not json')", "malformed JSON");
		failures += expectRefused(cx, scope, "PROBE_CTX.parseComponent('[1,2,3]')", "a JSON array");
		failures += expectRefused(cx, scope, "PROBE_CTX.parseComponent('{\"x\":1}')", "no component key");
		failures += expectRefused(cx, scope,
				"PROBE_CTX.parseComponent('{\"component\":\"there_is_no_such_thing\",\"x\":0,\"y\":0,\"width\":1,\"height\":1}')",
				"an unknown component name");
		failures += expectRefused(cx, scope, "PROBE_CTX.parseComponent('')", "an empty string");

		/* The registry's own list has to be in the message, or the author is told "unknown" and left
		   to guess the spelling. */
		try {
			cx.evaluateString(scope, "PROBE_CTX.parseComponent('{\"component\":\"nope\"}')", "probe", 1, null);
		} catch (Exception expected) {
			final String message = String.valueOf(expected.getMessage());
			if (message.contains("arrival_eta") && message.contains("clock")) {
				System.out.println("OK   the unknown-component message lists the known types");
			} else {
				System.out.println("FAIL the unknown-component message does not list the known types: " + message);
				failures++;
			}
		}
		return failures;
	}

	/**
	 * Asserts that an expression throws, and says which refusal it was.
	 *
	 * <p>Rhino wraps a Java exception, so the message is searched rather than the type compared —
	 * what matters is that the author gets told what was wrong with the declaration.</p>
	 *
	 * @return the number of failures
	 */
	private static int expectRefused(Context cx, Scriptable scope, String expression, String what) {
		try {
			final Object value = cx.evaluateString(scope, expression, "probe", 1, null);
			System.out.println("FAIL ctx.parseComponent() accepted " + what + " and returned " + value);
			return 1;
		} catch (Exception expected) {
			final String message = String.valueOf(expected.getMessage());
			if (message.contains("parseComponent")) {
				System.out.println("OK   refuses " + what);
				return 0;
			}
			System.out.println("FAIL " + what + " was refused without naming the call: " + message);
			return 1;
		}
	}

	/** Evaluates an expression in the scope and returns its value. */
	private static Object evaluate(Context cx, Scriptable scope, String expression) {
		return cx.evaluateString(scope, expression, "probe", 1, null);
	}

	/**
	 * Pins the one part of the arrivals API that a preset can see and this port got wrong.
	 *
	 * <p>JCM 2.x's {@code ArrivalsWrapper.get} is
	 * {@code i >= arrivals.size() ? null : ...}, and presets rely on it to leave a row blank —
	 * HKR's board loops over four rows and draws only {@code if (train)}. This port used to
	 * return a placeholder object instead, so that guard was always true and every preset
	 * filled its empty rows with a phantom train. The placeholder is gone; this makes sure it
	 * does not come back, because nothing else can tell the two behaviours apart.</p>
	 *
	 * @return the number of failures
	 */
	private static int checkArrivalsContract(PIDSWrapper pids) {
		int failures = 0;
		final int size = pids.arrivals().size();

		for (int index : new int[]{-1, size, size + 1, size + 8}) {
			if (pids.arrivals().get(index) != null) {
				System.out.println("FAIL arrivals().get(" + index + ") is not null past the end"
						+ " -- a preset's `if (train)` guard would draw a phantom row");
				failures++;
			}
		}
		if (size > 0 && pids.arrivals().get(0) == null) {
			System.out.println("FAIL arrivals().get(0) is null with " + size + " arrivals");
			failures++;
		}

		/* The other half of the contract: the engine retries a throwing preset with this
		   wrapper, so every accessor on the placeholder has to answer instead of throwing. */
		final PIDSWrapper lenient = pids.withLenientArrivals();
		if (!lenient.isLenientArrivals()) {
			System.out.println("FAIL withLenientArrivals() did not produce a lenient wrapper");
			failures++;
		}
		final PIDSWrapper.Arrival placeholder = lenient.arrivals().get(size);
		if (placeholder == null) {
			System.out.println("FAIL the lenient wrapper returns null past the end"
					+ " -- a preset that does not check would throw again on the retry");
			failures++;
		} else {
			try {
				final long eta = placeholder.arrivalTime();
				final String destination = placeholder.destination();
				final int cars = placeholder.carCount();
				final String routeNumber = placeholder.routeNumber();
				final PIDSWrapper.RouteInfo route = placeholder.route();
				final boolean departed = placeholder.departed();
				if (destination == null || routeNumber == null) {
					System.out.println("FAIL the placeholder returned a null string accessor");
					failures++;
				}
				if (route != null) {
					System.out.println("FAIL the placeholder resolved a route; a preset would take"
							+ " the empty platform for a real train");
					failures++;
				}
				System.out.println("OK   placeholder past the end: eta=" + (eta > 0 ? "set" : "unset")
						+ " cars=" + cars + " route=none departed=" + departed);
			} catch (Exception e) {
				System.out.println("FAIL an accessor on the placeholder threw: " + e);
				failures++;
			}
		}

		if (failures == 0) {
			System.out.println("OK   arrivals().get() is null past the end of a "
					+ size + "-arrival list, as JCM 2.x documents");
		}
		return failures;
	}

	/**
	 * Runs one frame with the fallback wrapper the engine retries with.
	 *
	 * <p>When a preset throws, {@code RenderPIDSBase} retries the frame with
	 * {@code arrivals().get(i)} handing out a placeholder. That retry only helps if the preset
	 * can actually finish against it, and the presets it exists for are exactly the ones that
	 * cannot be run any other way — so this exercises it on every preset the check is given,
	 * guarded or not. A guarded preset renders the same either way; an unguarded one is the
	 * case being proved.</p>
	 *
	 * @return the number of failures
	 */
	private static int checkLenientRetryRenders(Context cx, Scriptable scope, ScriptableObject state,
												PIDSWrapper pids) {
		final ScriptRenderContext ctx = ScriptRenderContext.dryRun(pids.width, pids.height, 1F);
		final Object fn = scope.get("render", scope);
		if (!(fn instanceof Function)) {
			return 0;
		}
		try {
			((Function) fn).call(cx, scope, scope, new Object[]{ctx, state, pids.withLenientArrivals()});
			System.out.println("OK   render() also completes against the placeholder arrivals ("
					+ ctx.recordedCalls().size() + " calls), which is what the engine retries with");
			return 0;
		} catch (Exception e) {
			System.out.println("FAIL render() threw against the placeholder arrivals too, so the retry"
					+ " cannot rescue this preset: " + e);
			return 1;
		}
	}

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
	 *
	 * <p>The path rules are the engine's own, not a relaxed version of them: a preset that reaches
	 * out of its resource root is refused here exactly as it would be in game, and the file is not
	 * opened. {@link ScriptPathCheck} is where that is asserted; this keeps a real pack from
	 * quietly succeeding at something the game would refuse.</p>
	 */
	private static void installFileInclude(final Context cx, final Scriptable scope) {
		ScriptableObject.putProperty(scope, "include", new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
				if (args.length == 0 || args[0] == null) {
					return Undefined.instance;
				}
				final String reference = Context.toString(args[0]);
				try {
					ScriptPaths.checkReference(reference);
				} catch (ScriptPaths.RejectedPathException refused) {
					ScriptPaths.report("include()", refused);
					return Undefined.instance;
				}
				final int colon = reference.indexOf(':');
				final String namespace = colon < 0 ? "minecraft" : reference.substring(0, colon);
				final String path = colon < 0 ? reference : reference.substring(colon + 1);

				for (Path root : RESOURCE_ROOTS) {
					final Path base = root.resolve("assets").resolve(namespace);
					final Path candidate;
					try {
						/* Resolved under assets/<namespace>/ and checked against it, so a ".."
						   that survived the reference rules cannot leave the root either. */
						candidate = ScriptPaths.resolveWithin(base, path.split("/"));
					} catch (Exception refused) {
						System.out.println("     include refused (outside " + root + "): " + reference);
						continue;
					}
					final Path actual = Files.isRegularFile(candidate) ? candidate : caseInsensitiveFile(base, path);
					if (actual != null) {
						try {
							final String text = new String(Files.readAllBytes(actual), StandardCharsets.UTF_8);
							context.evaluateString(s, text, actual.toString(), 1, null);
							System.out.println("     included " + reference + " -> " + actual.getFileName()
									+ (actual.equals(candidate) ? "" : " (case-insensitive fallback)"));
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

	/**
	 * Finds a file whose name differs from {@code relative} only in case, under {@code base}.
	 *
	 * <p>The headless half of the tolerance the engine applies in game. Minecraft's resource
	 * manager is indexed by lower case, so a pack that stored {@code Digital_Rail.js} is found
	 * there through the folded name; off the disk there is no index, so the directory is listed
	 * instead.</p>
	 *
	 * <p><b>Every result comes out of a directory listing</b>, including an exact one. That is
	 * not redundancy: on a case-insensitive filesystem — this project's own Windows volume, and
	 * the default on macOS — a {@link Path} built from {@code digital_rail.js} compares equal to
	 * itself and reports the name it was asked for, while the file on disk is
	 * {@code Digital_Rail.js}. Returning the value as constructed would hand the engine a
	 * location whose spelling matches nothing in the pack. Listing the directory is what makes
	 * the answer the on-disk name.</p>
	 *
	 * <p>Only the last segment is matched loosely; the directories above it must match as
	 * written. Every result stays inside {@code base}, because each step descends into a child of
	 * the directory it is already in.</p>
	 *
	 * @return the file, or {@code null} when no spelling of it exists
	 */
	static Path caseInsensitiveFile(Path base, String relative) {
		final String[] segments = relative.split("/");
		Path current = base;
		for (int i = 0; i < segments.length; i++) {
			final String segment = segments[i];
			if ("..".equals(segment)) {
				/* The caller has already refused a reference that walks out; refusing it again here
				   is what keeps that true if the call order ever changes. */
				return null;
			}
			final boolean last = i == segments.length - 1;
			/* Every candidate is an entry of a real directory listing, which is the point: a Path
			   built from a name the pack did not use reports the name it was asked for on a
			   case-insensitive filesystem, so the listing is the only source of the real spelling.
			   The listing also supplies the exact entry, and deduplicating by name keeps one entry
			   per file however the filesystem compares them. */
			final java.util.Map<String, Path> candidates = new java.util.LinkedHashMap<>();
			try (java.util.stream.Stream<Path> children = Files.list(current)) {
				children.filter(child -> child.getFileName().toString().equalsIgnoreCase(segment))
						.filter(child -> last ? Files.isRegularFile(child) : Files.isDirectory(child))
						.forEach(child -> candidates.putIfAbsent(child.getFileName().toString(), child));
			} catch (Exception e) {
				return null;
			}
			if (candidates.isEmpty()) {
				return null;
			}
			/* Deterministic when a pack ships two spellings, and "as written" wins when one of them
			   is exact -- so a reference that was already correct never resolves elsewhere. */
			final java.util.List<String> names = new java.util.ArrayList<>(candidates.keySet());
			names.sort(java.util.Comparator
					.comparing((String name) -> !name.equals(segment))
					.thenComparing(java.util.Comparator.naturalOrder()));
			final Path chosen = candidates.get(names.get(0));
			if (!last) {
				current = chosen;
			} else {
				return chosen;
			}
		}
		return null;
	}

	/** Resolves a resource root from the command line, tolerating a trailing separator. */
	@SuppressWarnings("unused")
	private static Path root(String value) {
		return new File(value).toPath();
	}
}
