package com.jsblock.script;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One PIDS panel's script, and the thread machinery that runs it off the render thread.
 *
 * <p>This is the port of JCM 2.x/v2's {@code PIDSScriptInstance}: the compiled script
 * ({@link ScriptEngine.Program}), the {@code state} object it owns, the last draw-call list it
 * produced, and a serial execution gate. The renderer submits one {@code render()} pass at a time
 * and replays {@link #lastCalls(BlockPos)} meanwhile; a new list is published only when a pass
 * completed without throwing.</p>
 *
 * <h2>Two halves, one state</h2>
 * <p>A double-sided PIDS is one compiled program rendered from two blocks, each with its own
 * wrapper ({@code pids.isKeyBlock()} differs). Both halves must keep their own recorded calls --
 * otherwise the halves overwrite each other's frame -- while still sharing the one {@code state}
 * object the preset expects. So the recorded lists, the in-flight gates and the one-time first
 * frame are kept per <b>render block position</b>, and the program itself is entered under one
 * lock. That reproduces the previous inline order (two {@code render()} calls per frame, one per
 * half, same state) without letting two Rhino calls touch the same scope concurrently.</p>
 *
 * <h2>Threading model</h2>
 * <p>All instances share one bounded {@link ExecutorService}. Serialisation is per half, through
 * an {@link AtomicBoolean}: {@code submitRender} succeeds only if that half is idle, so a slow
 * preset simply misses frames instead of queueing them up. A thread per panel was rejected: a
 * player can have dozens of panels in view, and each thread would hold a stack and a Rhino context
 * for a script that runs for a millisecond or two per frame. The shared pool bounds the thread
 * count independently of how many panels exist.</p>
 *
 * <h2>First frame</h2>
 * <p>A half that has never run has nothing to replay. Running its first pass asynchronously would
 * therefore show one empty frame — and for HKR's preset, whose {@code create()} takes 380-500 ms, a
 * very visible one. {@link #runFirstFrame} is called once per half, synchronously, on the render
 * thread; its calls are recorded and replayed in the same frame, and every later pass is
 * asynchronous. The second half of a panel pays only its {@code render()} on that first frame,
 * because {@code create()} has already run.</p>
 *
 * <h2>Failures</h2>
 * <p>A throw on the script thread is caught here, stored in {@link #lastError()}, and the previous
 * list for that half keeps being replayed — a broken frame must not turn a working board black.
 * {@link ScriptEngine.Program} has already logged it and queued any player-facing notice; this
 * class never rethrows onto the render thread.</p>
 */
public final class ScriptInstance {

	/**
	 * Shared workers. Two to four threads is enough to keep a handful of visible panels current
	 * without competing with the render thread for every core.
	 */
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
			Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
			new ThreadFactory() {
				private final AtomicInteger counter = new AtomicInteger();

				@Override
				public Thread newThread(Runnable runnable) {
					final Thread thread = new Thread(runnable,
							"jsblock-pids-script-" + counter.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				}
			});

	/** One rendered half of a panel: its own call list and gates. */
	private static final class Half {
		/** Whether a pass for this half is in flight. */
		final AtomicBoolean running = new AtomicBoolean();
		/** The one-time synchronous first frame for this half. */
		final AtomicBoolean firstFrame = new AtomicBoolean();
		/** How many times that first frame actually ran. */
		final AtomicInteger firstFrameRuns = new AtomicInteger();
		/** Last successfully recorded frame for this half; replayed until replaced. */
		volatile List<RecordedCall> lastCalls = Collections.emptyList();
		/** Whether that frame uploaded a GraphicsTexture; for the frame statistics. */
		volatile boolean lastPassRedrew;
	}

	private final ScriptEngine.Program program;
	/**
	 * One recorded frame for the whole panel, shared by both faces.
	 *
	 * <p>A two-block PIDS renders the same panel from both halves. When each half kept its own
	 * list they could sit one frame apart — one face showing the new canvas, the other the
	 * previous one — and since both are drawn at the same place that reads as flicker. One
	 * list makes the two faces identical by construction, and halves the script work.</p>
	 */
	private final Half half = new Half();
	/** The last failure, or {@code null} after a successful pass. */
	private volatile String lastError;
	/** Wall time of the last completed pass. */
	private volatile double lastExecutionMs;
	/** Set once a pass has completed without throwing. */
	private volatile boolean succeeded;
	/** Set by {@link #requestDispose()}; blocks further submissions. */
	private final AtomicBoolean disposed = new AtomicBoolean();
	/** Ensures {@code dispose()} is queued once. */
	private final AtomicBoolean disposeScheduled = new AtomicBoolean();
	/** Ensures {@code dispose()} runs once. */
	private final AtomicBoolean disposeDone = new AtomicBoolean();

	public ScriptInstance(ScriptEngine.Program program) {
		if (program == null) {
			throw new IllegalArgumentException("ScriptInstance needs a compiled program");
		}
		this.program = program;
	}

	private Half half(BlockPos pos) {
		return half;
	}

	/** @return the compiled script this instance owns. */
	public ScriptEngine.Program program() {
		return program;
	}

	/**
	 * Runs {@code create() + render()} for one half on the calling thread, exactly once per half.
	 *
	 * <p>Called by the renderer on the frame that first needs the panel, so that
	 * {@link #lastCalls(BlockPos)} has something to replay before the frame is drawn. The passed
	 * context must be a recording one ({@link ScriptRenderContext#recording}).</p>
	 *
	 * @return {@code true} when this call performed the first frame for this half, {@code false}
	 * when it had already run (or another pass was in flight)
	 */
	public boolean runFirstFrame(ScriptRenderContext recording, PIDSWrapper pids, BlockPos pos) {
		if (disposed.get()) {
			return false;
		}
		final Half half = half(pos);
		if (!half.running.compareAndSet(false, true)) {
			return false;
		}
		if (!half.firstFrame.compareAndSet(false, true)) {
			half.running.set(false);
			return false;
		}
		half.firstFrameRuns.incrementAndGet();
		try {
			execute(recording, pids, half);
		} finally {
			half.running.set(false);
		}
		return true;
	}

	/**
	 * Submits one {@code render()} pass for one half, if that half is idle.
	 *
	 * <p>Called from the render thread and returns immediately. The wrapper and the recording
	 * context must both be immutable snapshots of this frame; the job owns them afterwards.</p>
	 *
	 * @return {@code true} when a pass was submitted, {@code false} when one was already running
	 * (or the instance has been disposed)
	 */
	public boolean submitRender(ScriptRenderContext recording, PIDSWrapper pids, BlockPos pos) {
		if (disposed.get()) {
			return false;
		}
		final Half half = half(pos);
		if (!half.running.compareAndSet(false, true)) {
			return false;
		}
		try {
			EXECUTOR.execute(() -> {
				try {
					execute(recording, pids, half);
				} finally {
					half.running.set(false);
				}
			});
			return true;
		} catch (RejectedExecutionException e) {
			lastError = "script executor rejected the task: " + e.getMessage();
			half.running.set(false);
			return false;
		}
	}

	/**
	 * The pass body; runs on whichever thread owns the half's gate.
	 *
	 * <p>The program lock is what keeps the two halves of a double-sided panel from entering the
	 * same Rhino scope at once. It is held for the whole {@code create()}/{@code render()} call,
	 * which is also why a slow preset delays only other passes of the same panel.</p>
	 */
	private void execute(ScriptRenderContext recording, PIDSWrapper pids, Half half) {
		final long started = System.nanoTime();
		final long uploadsBefore = GraphicsTexture.uploadCalls();
		try {
			final boolean drawn;
			synchronized (program) {
				program.start(pids);
				drawn = program.renderRecovering(recording, pids);
			}
			if (drawn) {
				/* Publish only a completed frame. A partial list from a throw would drop the
				   background and everything the script drew before the failing statement. */
				half.lastCalls = recording.recordedCallList();
				lastError = null;
				succeeded = true;
			} else {
				lastError = program.getLastError() == null
						? "render() failed without a message" : program.getLastError();
			}
		} catch (Throwable t) {
			/* Never let a script or engine failure reach the render thread. */
			lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
			com.jsblock.Joban.LOGGER.error("[Joban Client] PIDS script \"{}\" failed off-thread: {}",
					program.getKey(), t.toString());
		} finally {
			half.lastPassRedrew = GraphicsTexture.uploadCalls() > uploadsBefore;
			lastExecutionMs = (System.nanoTime() - started) / 1_000_000.0;
		}
	}

	/** @return the last successfully recorded frame for a half; never {@code null}, maybe empty. */
	public List<RecordedCall> lastCalls(BlockPos pos) {
		return half(pos).lastCalls;
	}

	/** @return the last failure, or {@code null} when the last completed pass succeeded. */
	public String lastError() {
		return lastError;
	}

	/** @return whether any half has a pass in flight. */
	public boolean isRunning() {
		return half.running.get();
	}

	/** @return whether any pass has completed without throwing. */
	public boolean hasSucceeded() {
		return succeeded;
	}

	/** @return how many times the synchronous first frame has run across every half. */
	public int firstFrameRuns() {
		return half.firstFrameRuns.get();
	}

	/** @return how many times the synchronous first frame ran for one half. */
	public int firstFrameRuns(BlockPos pos) {
		return half(pos).firstFrameRuns.get();
	}

	/** @return milliseconds spent in the last completed pass. */
	public double getLastExecutionMs() {
		return lastExecutionMs;
	}

	/** @return whether the last completed pass uploaded a canvas (used by the frame statistics). */
	public boolean lastPassRedrew(BlockPos pos) {
		return half(pos).lastPassRedrew;
	}

	/** @return whether this instance has been asked to dispose. */
	public boolean isDisposed() {
		return disposed.get();
	}

	/**
	 * Asks for {@code dispose()} to run on the script thread, after any pass in flight.
	 *
	 * <p>Non-blocking: the render thread calls this when a world unloads or resources reload, and
	 * must not wait for a slow script to finish.</p>
	 */
	public void requestDispose() {
		if (disposed.getAndSet(true)) {
			return;
		}
		if (!disposeScheduled.compareAndSet(false, true)) {
			return;
		}
		try {
			EXECUTOR.execute(this::runDispose);
		} catch (RejectedExecutionException ignored) {
			/* The executor is gone; so is the process that owned the script. */
		}
	}

	private void runDispose() {
		try {
			synchronized (program) {
				if (disposeDone.compareAndSet(false, true)) {
					program.dispose();
				}
			}
		} catch (Throwable t) {
			com.jsblock.Joban.LOGGER.warn("[Joban Client] PIDS script \"{}\" threw in dispose(): {}",
					program.getKey(), t.toString());
		}
	}

	/**
	 * Waits until no half has a pass in flight.
	 *
	 * <p>For the headless check only: the renderer never blocks on a script.</p>
	 *
	 * @return {@code true} when idle, {@code false} on timeout
	 */
	public boolean awaitIdle(long timeoutMillis) {
		final long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
		while (isRunning()) {
			if (System.nanoTime() >= deadline) {
				return false;
			}
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}
}
