package com.jsblock.script;

import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live {@link ScriptInstance}s, keyed by panel.
 *
 * <p>Port of JCM 2.x/v2's {@code ScriptInstanceManager}. A panel is identified by the same key the
 * compiled {@link ScriptEngine.Program} already uses — preset id plus the key block's position —
 * so the two halves of a two-block PIDS share one state object and one execution gate.</p>
 *
 * <h2>Lifetime</h2>
 * <p>Instances are dropped and disposed when the resource manager reloads (the scripts they were
 * compiled from are gone) and when the client moves to a different {@link Level}. The latter is
 * detected on the render path by {@link #beginFrame(Level)}: a world change has no client event in
 * this branch, and a stale instance would otherwise keep its Rhino scope and its
 * {@code GraphicsTexture}s for the rest of the session.</p>
 *
 * <h2>The fallback switch</h2>
 * <p>{@code -Djsblock.pids.asyncScript=false} restores the previous inline execution exactly:
 * {@link #asyncEnabled()} is {@code false}, the renderer never creates or submits an instance, and
 * every frame runs {@code create()}/{@code render()} on the render thread as before. It is the
 * one-line escape hatch for a user hitting a script behaviour the asynchronous model exposes.</p>
 */
public final class ScriptInstances {

	/**
	 * {@code -Djsblock.pids.asyncScript}; on by default.
	 *
	 * <p>Volatile rather than final so the headless check can exercise both modes in one JVM. The
	 * property is still the only way a player changes it.</p>
	 */
	private static volatile boolean asyncEnabled =
			! "false".equalsIgnoreCase(System.getProperty("jsblock.pids.asyncScript", "true"));

	private static final Map<String, ScriptInstance> INSTANCES = new ConcurrentHashMap<>();

	/** The world the current instances belong to, held weakly so a disconnect can be collected. */
	private static volatile WeakReference<Level> world = new WeakReference<>(null);
	/** Whether {@link #world} has ever named a world; distinguishes "none yet" from "left". */
	private static volatile boolean worldKnown;

	private ScriptInstances() {
	}

	/** @return whether the asynchronous engine is enabled. */
	public static boolean asyncEnabled() {
		return asyncEnabled;
	}

	/** For the headless check; a player uses the system property. */
	static void setAsyncEnabledForCheck(boolean enabled) {
		asyncEnabled = enabled;
	}

	/**
	 * Notices a world change and drops every instance that belonged to the old one.
	 *
	 * <p>Called once per client tick with {@code client.level} (which is {@code null} in the main
	 * menu, so a disconnect releases the instances too), and defensively once per scripted panel
	 * per frame. The common path is two field reads and an identity comparison.</p>
	 */
	public static void beginFrame(Level level) {
		if (level == null) {
			if (worldKnown) {
				worldKnown = false;
				world = new WeakReference<>(null);
				reset();
			}
			return;
		}
		final Level previous = world.get();
		if (!worldKnown) {
			worldKnown = true;
			world = new WeakReference<>(level);
			return;
		}
		if (previous == level) {
			return;
		}
		/* Either a different world, or the old one was collected; both mean the instances are
		   stale. */
		world = new WeakReference<>(level);
		reset();
	}

	/** @return the instance for a compiled program, creating it on first use. */
	public static ScriptInstance instanceFor(ScriptEngine.Program program) {
		if (program == null) {
			throw new IllegalArgumentException("No compiled program to run");
		}
		return INSTANCES.computeIfAbsent(program.getKey(), key -> new ScriptInstance(program));
	}

	/** @return the live instances; a snapshot, for the debug overlay and the check. */
	public static List<ScriptInstance> instances() {
		return new ArrayList<>(INSTANCES.values());
	}

	/** @return how many panels currently own an instance. */
	public static int instanceCount() {
		return INSTANCES.size();
	}

	/**
	 * Drops every instance and asks each to dispose on its script thread.
	 *
	 * <p>Non-blocking: a script may be mid-render, and the callers are the render thread and the
	 * resource-reload path. The dispose task is queued behind that render pass by
	 * {@link ScriptInstance#requestDispose()}.</p>
	 */
	public static void reset() {
		final List<ScriptInstance> dropped = new ArrayList<>(INSTANCES.values());
		INSTANCES.clear();
		/* The compiled programs carry the old world's state object; a new world must run create()
		   again. Clearing the cache here also prevents a dispose task from the old instance from
		   touching a program a freshly created instance is using. */
		ScriptEngine.invalidatePrograms();
		for (ScriptInstance instance : dropped) {
			try {
				instance.requestDispose();
			} catch (Throwable t) {
				com.jsblock.Joban.LOGGER.warn("[Joban Client] Could not dispose a PIDS script instance: {}",
						t.toString());
			}
		}
	}
}
