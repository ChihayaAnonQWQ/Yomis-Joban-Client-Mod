package com.jsblock.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds messages that must not be delivered from inside a render call.
 *
 * <p>A PIDS script throws during block-entity rendering, where talking to the player is not
 * safe: the render thread is mid-frame, and a block entity renderer may run for a block the
 * player cannot even see. So the failure is logged where it happens and the player-facing
 * half is queued here, to be delivered from the client tick instead.</p>
 *
 * <p>Port of JCM 2.x's {@code com.lx862.mtrscripting.mod.gui.hud.ScriptErrorNotifier}, which
 * does the same thing for the same reason.</p>
 */
public final class ScriptErrorNotifier {

	private final List<Runnable> queue = new ArrayList<>();

	/** Queues a notification for the next {@link #flush()}. */
	public void queue(Runnable callback) {
		if (callback != null) {
			queue.add(callback);
		}
	}

	/** Drops everything queued but not yet delivered; used when the resource manager reloads. */
	public void reset() {
		queue.clear();
	}

	/**
	 * Delivers everything queued. Called from the client tick, where a player and a chat
	 * screen are known to exist.
	 *
	 * <p>A callback that throws is dropped rather than allowed to abort the flush: these run
	 * once per tick on the client thread, and losing the rest of the queue because one
	 * notification misbehaved would be worse than the failure it was reporting.</p>
	 */
	public void flush() {
		if (queue.isEmpty()) {
			return;
		}
		final List<Runnable> pending = new ArrayList<>(queue);
		queue.clear();
		for (Runnable callback : pending) {
			try {
				callback.run();
			} catch (Exception e) {
				com.jsblock.Joban.LOGGER.error("[Joban Client] Failed to deliver a PIDS script notification: " + e);
			}
		}
	}

	/** @return how many notifications are waiting; used by the debug overlay. */
	public int pendingCount() {
		return queue.size();
	}
}
