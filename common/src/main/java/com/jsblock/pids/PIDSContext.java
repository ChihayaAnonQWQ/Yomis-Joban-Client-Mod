package com.jsblock.pids;

import mtr.data.ScheduleEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of everything a PIDS component may need while drawing one frame.
 *
 * <p>This is the MTR 3 counterpart of JCM 2.x's
 * {@code com.lx862.jcm.mod.data.pids.preset.PIDSContext}. The important difference is the
 * arrival model: MTR 4 hands components rich {@code org.mtr.core.operation.ArrivalResponse}
 * objects, whereas MTR 3 only exposes {@link ScheduleEntry}, which carries
 * {@code arrivalMillis}, {@code trainCars}, {@code routeId} and {@code currentStationIndex}.
 * Everything else a component needs (route name/colour/destination, station name, platform
 * name) is looked up lazily through {@link PIDSData}.</p>
 *
 * <h2>Rows</h2>
 * <p>A component's {@code row} option is a <b>display row</b>, not an index into the raw
 * arrival list, and it follows exactly the rule the built-in renderers use
 * ({@code RenderLCDPIDS} / {@code RenderRVPIDS}): a row flagged hidden in
 * {@code hideArrivals} does <b>not</b> consume an arrival, so the retained arrival simply
 * surfaces in the next visible row. Components therefore address the same row a user would
 * point at on screen, regardless of how many rows the preset hides.</p>
 *
 * @see PIDSData
 */
public class PIDSContext {

	/** World the PIDS block lives in. */
	public final Level world;
	/** Position of the PIDS block (the "origin" half of a two-block PIDS). */
	public final BlockPos pos;
	/** Facing of the PIDS block, used for orienting textures. */
	public final Direction facing;
	/** Per-row custom messages, already variable-substituted by the caller. */
	public final String[] customMessages;
	/** Sorted upcoming arrivals for the platform(s) this PIDS watches, before row mapping. */
	public final List<ScheduleEntry> scheduleList;
	/** Platform ids this PIDS is filtered to; empty means "nearest platform". */
	public final List<Long> platformIds;
	/** Per-display-row hide flags coming from the block entity and the preset. */
	public final boolean[] hideArrivals;
	/** Partial tick time, for animated components. */
	public final double deltaTime;
	/** Client game tick counter, for time-based cycling. */
	public final long gameTick;

	/** Display row to arrival, honouring {@link #hideArrivals}. */
	private final ScheduleEntry[] rowArrivals;

	public PIDSContext(Level world, BlockPos pos, Direction facing, String[] customMessages,
					   List<ScheduleEntry> scheduleList, List<Long> platformIds,
					   boolean[] hideArrivals, double deltaTime, long gameTick) {
		this.world = world;
		this.pos = pos;
		this.facing = facing;
		this.customMessages = customMessages == null ? new String[0] : customMessages;
		this.scheduleList = scheduleList == null ? Collections.emptyList() : scheduleList;
		this.platformIds = platformIds == null ? Collections.emptyList() : platformIds;
		this.hideArrivals = hideArrivals == null ? new boolean[0] : hideArrivals;
		this.deltaTime = deltaTime;
		this.gameTick = gameTick;
		this.rowArrivals = buildRowArrivals(this.scheduleList, this.hideArrivals);
	}

	private static ScheduleEntry[] buildRowArrivals(List<ScheduleEntry> schedule, boolean[] hide) {
		final int rows = Math.max(hide.length, schedule.size());
		final ScheduleEntry[] result = new ScheduleEntry[rows];
		int next = 0;
		for (int row = 0; row < rows; row++) {
			if (row < hide.length && hide[row]) {
				// Hidden rows do not consume an arrival, matching the entryIndex
				// consumption rule of the built-in renderers.
				continue;
			}
			result[row] = next < schedule.size() ? schedule.get(next) : null;
			next++;
		}
		return result;
	}

	/** @return how many display rows this PIDS has. */
	public int getRowCount() {
		return rowArrivals.length;
	}

	/**
	 * @param row a <b>display row</b> index
	 * @return the arrival shown on that row, or {@code null} when the row is hidden or past
	 * the end of the arrival list
	 */
	public ScheduleEntry arrival(int row) {
		return row >= 0 && row < rowArrivals.length ? rowArrivals[row] : null;
	}

	/** @return the arrival on display row 0, or {@code null}. */
	public ScheduleEntry firstArrival() {
		return arrival(0);
	}

	/** @return every arrival that is actually shown on some display row, in row order. */
	public List<ScheduleEntry> visibleArrivals() {
		final List<ScheduleEntry> visible = new ArrayList<>();
		for (ScheduleEntry entry : rowArrivals) {
			if (entry != null) {
				visible.add(entry);
			}
		}
		return visible;
	}

	/** @return {@code true} when the given display row is flagged hidden. */
	public boolean isRowHidden(int row) {
		return row >= 0 && row < hideArrivals.length && hideArrivals[row];
	}

	/** @return the custom message for a display row, or an empty string. */
	public String customMessage(int row) {
		return row >= 0 && row < customMessages.length && customMessages[row] != null ? customMessages[row] : "";
	}

	/**
	 * @return the platform id this PIDS primarily watches, or {@code 0} when unknown
	 *
	 * <p>MTR's rule, from {@code IPIDS.TileEntityPIDS#getPlatformId}: the platform closest to
	 * the block. {@code platformIds} is a {@code Set<Long>}, so its first element in list form
	 * is whatever hash order produced, and for a panel serving several platforms that is
	 * regularly a station elsewhere on the line — {@code PlatformComponent} and
	 * {@code StationNameComponent} then named the wrong station. The set is only the fallback
	 * for when no platform is close enough to be found.</p>
	 */
	public long primaryPlatformId() {
		final long closest = PIDSData.closestPlatformId(pos);
		if (closest != 0) {
			return closest;
		}
		return platformIds.isEmpty() ? 0 : platformIds.get(0);
	}

	/** @return a mutable copy of the platform ids, never {@code null}. */
	public List<Long> platformIdsCopy() {
		return new ArrayList<>(platformIds);
	}
}
