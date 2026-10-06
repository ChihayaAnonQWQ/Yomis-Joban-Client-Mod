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
			System.out.println("usage: ScriptApiCheck <script.js> [resourceRoot ...]");
			System.exit(2);
		}
		final Path script = Paths.get(args[0]);
		for (int i = 1; i < args.length; i++) {
			RESOURCE_ROOTS.add(Paths.get(args[i]));
		}
		if (!Files.isRegularFile(script)) {
			System.out.println("FAIL script not found: " + script);
			System.exit(2);
		}

		System.out.println("== script: " + script);
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

			final PIDSWrapper pids = stubPids();
			System.out.println("OK   stub pids: " + pids.width + "x" + pids.height
					+ " type=" + pids.type + " arrivals=" + pids.arrivals().size());

			failures += callLifecycle(cx, scope, "create", state, pids);

			// render(ctx, state, pids) with a recording context.
			final ScriptRenderContext ctx = ScriptRenderContext.dryRun(pids.width, pids.height, 1F);
			failures += callRender(cx, scope, state, pids, ctx);

			final List<String> recorded = ctx.recordedCalls();
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

	private static PIDSWrapper stubPids() {
		final long now = System.currentTimeMillis();
		final List<ScheduleEntry> schedule = Arrays.asList(
				new ScheduleEntry(now + 90_000L, 6, 1L, 2),
				new ScheduleEntry(now + 260_000L, 4, 2L, 5)
		);
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
								  PIDSWrapper pids, ScriptRenderContext ctx) {
		final Object fn = scope.get("render", scope);
		if (!(fn instanceof Function)) {
			System.out.println("FAIL no render() defined");
			return 1;
		}
		try {
			((Function) fn).call(cx, scope, scope, new Object[]{ctx, state, pids});
			System.out.println("OK   render() ran");
			return 0;
		} catch (Exception e) {
			System.out.println("FAIL render() threw: " + e);
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
