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
