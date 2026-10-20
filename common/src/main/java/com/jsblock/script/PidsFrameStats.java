package com.jsblock.script;

import com.jsblock.Joban;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-frame section timings for a scripted PIDS, printed once a second when
 * {@code -Djsblock.pids.canvasStats=true} is set.
 *
 * <p>This is the second half of the {@link GraphicsTexture} canvas statistics: those say what the
 * canvas upload costs, these say where the rest of the frame goes. Together they answer the only
 * question a "this PIDS drops frames" report needs before anything is changed — which part of the
 * frame is expensive, and whether it is proportional to what the script draws or is a fixed cost
 * paid by every panel.</p>
 *
 * <h2>The sections</h2>
 * <ul>
 *   <li><b>A</b> — preparation, before the script is entered: the preset lookup, the custom
 *       messages, the platform filter and the schedule list, the {@link PIDSWrapper}, the panel
 *       transform, and the pixelation decisions. Everything a frame pays whether or not the
 *       script draws anything.</li>
 *   <li><b>B</b> — the script call itself ({@code create()}/{@code render()}), measured around
 *       the {@code Program} call and not around the whole frame. {@code create} is counted
 *       separately, because a first frame that runs it is not a normal frame.</li>
 *   <li><b>C</b> — draw-call execution, timed inside {@link ScriptRenderContext#draw}. In this
 *       port a call draws immediately rather than being queued for replay, so C is <em>inside</em>
 *       B: {@code B - C} is what the script's own interpretation costs, and C is what the
 *       geometry and text cost. JCM 2.x's equivalent is its replay, a span of its own there.</li>
 *   <li><b>D</b> — the remainder: the render state handed to the script, the text batch flush,
 *       the pixelation pass around the script, and the pop. Computed as {@code total - A - B} for
 *       each frame, so it cannot disagree with the total.</li>
 * </ul>
 *
 * <p>Two more numbers are collected because the sections cannot attribute the cost without
 * them:</p>
 * <ul>
 *   <li><b>api</b> — time spent inside this port's own script-visible Java objects
 *       ({@link PIDSWrapper} and friends), counted where they are entered. A large B with a large
 *       api share is our lookups; a large B with a small api share is Rhino interpreting the
 *       script.</li>
 *   <li><b>redrawFrames</b> — frames on which the preset uploaded its {@link GraphicsTexture},
 *       i.e. actually repainted its canvas. A canvas preset's redrawing frames and its
 *       re-submit-only frames cost very different amounts, and averaging them together hides
 *       both.</li>
 * </ul>
 *
 * <h2>Cost, and why this is safe to leave in</h2>
 * <p>Everything here is a static boolean test when the switch is off; when it is on it is a
 * handful of {@link System#nanoTime()} pairs per frame (plus one per draw call and one per
 * instrumented wrapper access), accumulated into primitives and formatted once a second. No
 * string is built, no object is allocated and no lock is taken on the hot path, and nothing is
 * logged unless a full second has passed.</p>
 *
 * <p>Only the render thread writes a frame, and it is the only thread that reads one back, so the
 * fields are deliberately unsynchronised. A two-block PIDS paints one face per half, so it is
 * rendered twice per frame; the records are keyed by preset and position, and each half gets its
 * own line rather than the two silently sharing one.</p>
 */
public final class PidsFrameStats {

	/**
	 * {@code -Djsblock.pids.canvasStats=true}, shared with {@link GraphicsTexture#upload()} so one
	 * switch turns on both the canvas and the frame breakdown.
	 */
	public static final boolean ENABLED = Boolean.getBoolean("jsblock.pids.canvasStats");

	/**
	 * Whether a frame is open right now, for the hot paths that would otherwise call
	 * {@link System#nanoTime()} for nothing. Package-visible so the wrapper accessors can test it.
	 */
	static boolean ACTIVE = false;

	/** The panel currently being rendered, or {@code null} between frames. */
	private static Record current = null;

	/** One panel's counters for one reporting window. */
	private static final class Record {
		final String label;
		/** Start of the reporting window; zero until the first frame. */
		long windowStart;
		/** Start of the frame being timed. */
		long frameStart;
		/** Section A of the frame being timed. */
		long frameA;
		/** Section B of the frame being timed. */
		long frameB;
		/** Whether the frame being timed repainted its canvas. */
		boolean frameRedraw;
		/** Whether the frame being timed ran {@code create()}. */
		long frameCreate;

		int frames;
		long totalNanos;
		long maxTotalNanos;

		long aNanos;
		long maxANanos;
		long bNanos;
		long maxBNanos;
		long cNanos;
		long maxCNanos;
		long dNanos;
		long maxDNanos;
		long createNanos;

		int redrawFrames;
		long redrawBNanos;
		int drawCalls;

		long apiNanos;
		long maxApiNanos;
		int apiCalls;

		Record(String label) {
			this.label = label;
		}

		void reset() {
			frames = 0;
			totalNanos = 0L;
			maxTotalNanos = 0L;
			aNanos = 0L;
			maxANanos = 0L;
			bNanos = 0L;
			maxBNanos = 0L;
			cNanos = 0L;
			maxCNanos = 0L;
			dNanos = 0L;
			maxDNanos = 0L;
			createNanos = 0L;
			redrawFrames = 0;
			redrawBNanos = 0;
			drawCalls = 0;
			apiNanos = 0L;
			maxApiNanos = 0L;
			apiCalls = 0;
		}
	}

	/** One record per panel; created lazily, never removed (a panel is a block position). */
	private static final Map<String, Record> RECORDS = new HashMap<>();

	private PidsFrameStats() {
	}

	// ------------------------------------------------------------------
	// Frame lifecycle, called from RenderPIDSBase
	// ------------------------------------------------------------------

	/**
	 * @return {@link System#nanoTime()} when the statistics are on, otherwise {@code 0}.
	 *
	 * <p>Taken at the very top of the block entity renderer, before the preset is even known, so
	 * that section A includes the work every panel does — the custom messages, the platform set,
	 * the schedule list — and not only the part after the preset was found.</p>
	 */
	public static long frameStart() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	/**
	 * Opens a frame for a scripted panel.
	 *
	 * @param startNanos what {@link #frameStart()} returned for this frame
	 * @param presetId   the preset being rendered
	 * @param pos        the block this half of the panel is rendered from
	 */
	public static void begin(long startNanos, String presetId, BlockPos pos) {
		if (!ENABLED) {
			return;
		}
		final String label = presetId + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
		Record record = RECORDS.get(label);
		if (record == null) {
			record = new Record(label);
			RECORDS.put(label, record);
		}
		record.frameStart = startNanos == 0L ? System.nanoTime() : startNanos;
		record.frameA = 0L;
		record.frameB = 0L;
		record.frameRedraw = false;
		record.frameCreate = 0L;
		current = record;
		ACTIVE = true;
	}

	/** Closes the frame's section A: everything up to the script call. */
	public static void endSectionA() {
		if (!ACTIVE) {
			return;
		}
		final Record record = current;
		record.frameA = System.nanoTime() - record.frameStart;
	}

	/** @return a mark for {@link #addSectionB(long)}, or {@code 0} when no frame is open. */
	public static long mark() {
		return ACTIVE ? System.nanoTime() : 0L;
	}

	/** Adds the span since {@code startNanos} to the frame's section B (the script call). */
	public static void addSectionB(long startNanos) {
		if (!ACTIVE || startNanos == 0L) {
			return;
		}
		current.frameB += System.nanoTime() - startNanos;
	}

	/** Records how long the frame's {@code create()} took; the frame itself is normal. */
	public static void addCreate(long nanos) {
		if (!ACTIVE || nanos <= 0L) {
			return;
		}
		current.frameCreate += nanos;
	}

	/**
	 * Closes the frame and, once a second, prints the line.
	 *
	 * <p>D is the residual {@code total - A - B}, which is why section D has no explicit
	 * instrumentation: whatever the two named spans do not cover is the rest of the frame.</p>
	 */
	public static void end() {
		if (!ACTIVE) {
			return;
		}
		ACTIVE = false;
		final Record record = current;
		current = null;
		if (record == null) {
			return;
		}

		final long now = System.nanoTime();
		final long total = now - record.frameStart;
		final long a = record.frameA;
		final long b = record.frameB;
		final long d = Math.max(0L, total - a - b);

		record.frames++;
		record.totalNanos += total;
		record.aNanos += a;
		record.bNanos += b;
		record.dNanos += d;
		record.createNanos += record.frameCreate;
		if (total > record.maxTotalNanos) {
			record.maxTotalNanos = total;
		}
		if (a > record.maxANanos) {
			record.maxANanos = a;
		}
		if (b > record.maxBNanos) {
			record.maxBNanos = b;
		}
		if (d > record.maxDNanos) {
			record.maxDNanos = d;
		}
		if (record.frameRedraw) {
			record.redrawFrames++;
			record.redrawBNanos += b;
		}

		if (record.windowStart == 0L) {
			record.windowStart = now;
			return;
		}
		final long elapsed = now - record.windowStart;
		if (elapsed >= 1_000_000_000L) {
			flush(record, elapsed);
		}
	}

	// ------------------------------------------------------------------
	// What the script did, recorded where it happens
	// ------------------------------------------------------------------

	/** Marks the current frame as one that repainted (and uploaded) its canvas. */
	public static void noteCanvasUpload() {
		if (!ACTIVE) {
			return;
		}
		current.frameRedraw = true;
	}

	/**
	 * Counts one draw call and the time it took.
	 *
	 * @param nanos time spent in {@code ScriptDrawCall.draw}, or in a parsed component's draw
	 */
	public static void noteDrawCall(long nanos) {
		if (!ACTIVE) {
			return;
		}
		final Record record = current;
		record.drawCalls++;
		record.cNanos += nanos;
		if (nanos > record.maxCNanos) {
			record.maxCNanos = nanos;
		}
	}

	/** @return a mark for {@link #apiEnd(long)}, or {@code 0} when the statistics are off. */
	public static long apiStart() {
		return ACTIVE ? System.nanoTime() : 0L;
	}

	/**
	 * Adds one instrumented wrapper access to the frame's api total.
	 *
	 * <p>Nested accesses are counted in full, so this is "time inside our Java objects", not the
	 * exclusive time of the outermost one. It is meant to separate our lookups from Rhino's
	 * interpretation, not to be added to B as if the two were disjoint.</p>
	 */
	static void apiEnd(long startNanos) {
		if (!ACTIVE || startNanos == 0L) {
			return;
		}
		final Record record = current;
		final long nanos = System.nanoTime() - startNanos;
		record.apiCalls++;
		record.apiNanos += nanos;
		if (nanos > record.maxApiNanos) {
			record.maxApiNanos = nanos;
		}
	}

	// ------------------------------------------------------------------
	// Reporting
	// ------------------------------------------------------------------

	private static void flush(Record record, long elapsedNanos) {
		final int frames = record.frames;
		if (frames == 0) {
			record.windowStart = System.nanoTime();
			return;
		}

		/* The slowest single frame's worst section, not the slowest section on average: a
		   diagnostic is for finding the frame the player felt, and one 8 ms redraw hides inside
		   an average of forty 0.5 ms frames. */
		final String worstSection;
		final long worstNanos;
		if (record.maxANanos >= record.maxBNanos && record.maxANanos >= record.maxCNanos) {
			worstSection = "A";
			worstNanos = record.maxANanos;
		} else {
			worstSection = record.maxBNanos >= record.maxCNanos ? "B" : "C";
			worstNanos = Math.max(record.maxBNanos, record.maxCNanos);
		}

		Joban.LOGGER.info(String.format(Locale.ROOT,
				"[Joban Client] PIDS script stats %s: frames=%d/s total avg=%.2f max=%.2f"
						+ " | A avg=%.2f max=%.2f"
						+ " | B avg=%.2f max=%.2f (create %.2f once)"
						+ " | C avg=%.2f max=%.2f, in B"
						+ " | D avg=%.2f max=%.2f"
						+ " | redrawFrames=%d (B avg=%.2f)"
						+ " | api avg=%.2f max=%.2f calls=%d/s"
						+ " | drawCalls avg=%.1f"
						+ " | worst=%s,%.2f",
				record.label, Math.round(frames * 1e9 / elapsedNanos),
				record.totalNanos / 1e6 / frames, record.maxTotalNanos / 1e6,
				record.aNanos / 1e6 / frames, record.maxANanos / 1e6,
				record.bNanos / 1e6 / frames, record.maxBNanos / 1e6,
				record.createNanos / 1e6,
				record.cNanos / 1e6 / frames, record.maxCNanos / 1e6,
				record.dNanos / 1e6 / frames, record.maxDNanos / 1e6,
				record.redrawFrames,
				record.redrawFrames == 0 ? 0.0 : record.redrawBNanos / 1e6 / record.redrawFrames,
				record.apiNanos / 1e6 / frames, record.maxApiNanos / 1e6,
				Math.round(record.apiCalls * 1e9 / elapsedNanos),
				record.drawCalls / (double) frames,
				worstSection, worstNanos / 1e6));

		record.windowStart = System.nanoTime();
		record.reset();
	}

	/** @return how many panels have reported at least one frame; for the headless check. */
	public static int trackedPanels() {
		return RECORDS.size();
	}
}
