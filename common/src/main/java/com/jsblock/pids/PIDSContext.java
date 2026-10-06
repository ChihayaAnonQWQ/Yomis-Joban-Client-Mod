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
	/** Sorted upcoming arrivals for the platform(s) this PIDS watches. */
	public final List<ScheduleEntry> scheduleList;
	/** Platform ids this PIDS is filtered to; empty means "nearest platform". */
	public final List<Long> platformIds;
	/** Per-row hide flags coming from the block entity and the preset. */
	public final boolean[] hideArrivals;
	/** Partial tick time, for animated components. */
	public final double deltaTime;
	/** Client game tick counter, for time-based cycling. */
	public final long gameTick;

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
	}

	/** @return the arrival at {@code index}, or {@code null} when there is none. */
	public ScheduleEntry arrival(int index) {
		return index >= 0 && index < scheduleList.size() ? scheduleList.get(index) : null;
	}

	/** @return the first (soonest) upcoming arrival, or {@code null}. */
	public ScheduleEntry firstArrival() {
		return arrival(0);
	}

	/** @return how many arrivals are visible (not hidden) from {@code from} onward. */
	public int visibleArrivalCount(int from) {
		int count = 0;
		for (int i = from; i < scheduleList.size(); i++) {
			if (!isRowHidden(i)) {
				count++;
			}
		}
		return count;
	}

	/** @return {@code true} when the given arrival row is flagged hidden. */
	public boolean isRowHidden(int row) {
		return row >= 0 && row < hideArrivals.length && hideArrivals[row];
	}

	/** @return the custom message for a row, or an empty string. */
	public String customMessage(int row) {
		return row >= 0 && row < customMessages.length && customMessages[row] != null ? customMessages[row] : "";
	}

	/** @return the platform id this PIDS primarily watches, or {@code 0} when unknown. */
	public long primaryPlatformId() {
		return platformIds.isEmpty() ? PIDSData.closestPlatformId(world, pos) : platformIds.get(0);
	}

	/** @return a mutable copy of the platform ids, never {@code null}. */
	public List<Long> platformIdsCopy() {
		return new ArrayList<>(platformIds);
	}
}
