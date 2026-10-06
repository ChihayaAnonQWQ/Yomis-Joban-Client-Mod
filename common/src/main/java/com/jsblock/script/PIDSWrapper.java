package com.jsblock.script;

import com.jsblock.pids.PIDSData;
import mtr.data.Platform;
import mtr.data.Route;
import mtr.data.ScheduleEntry;
import mtr.data.Station;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code pids} object handed to a script's {@code render(ctx, state, pids)}.
 *
 * <p>Port of JCM 2.x's {@code PIDSWrapper}. The fields scripts read most
 * ({@code width}, {@code height}, {@code type}, {@code rows}) are public, and the arrival
 * list is exposed through {@link Arrivals} / {@link Arrival}, which flatten MTR 3's
 * {@link ScheduleEntry} plus {@link PIDSData} lookups into the shape JCM 2.x scripts expect
 * from MTR 4's {@code ArrivalResponse}.</p>
 */
public class PIDSWrapper {

	/** PIDS type name, e.g. {@code "pids_rv"}. */
	public final String type;
	/** Number of arrival rows the block declares. */
	public final int rows;
	/** Panel width in script units; a background drawn at this size fills the panel. */
	public final int width;
	/** Panel height in script units. */
	public final int height;

	private final BlockPos blockPos;
	private final List<Long> platformIds;
	private final String[] customMessages;
	private final boolean[] rowHidden;
	private final List<ScheduleEntry> scheduleList;
	private final Arrivals arrivals;

	public PIDSWrapper(String type, int rows, int width, int height, BlockPos blockPos,
					   List<Long> platformIds, String[] customMessages, boolean[] rowHidden,
					   List<ScheduleEntry> scheduleList) {
		this.type = type;
		this.rows = rows;
		this.width = width;
		this.height = height;
		this.blockPos = blockPos;
		this.platformIds = platformIds == null ? new ArrayList<>() : new ArrayList<>(platformIds);
		this.customMessages = customMessages == null ? new String[0] : customMessages;
		this.rowHidden = rowHidden == null ? new boolean[0] : rowHidden;
		this.scheduleList = scheduleList == null ? new ArrayList<>() : new ArrayList<>(scheduleList);
		this.arrivals = new Arrivals(this.scheduleList);
	}

	// ------------------------------------------------------------------
	// API exposed to scripts
	// ------------------------------------------------------------------

	public Arrivals arrivals() {
		return arrivals;
	}

	/** @return the custom message for a row, or an empty string. */
	public String getCustomMessage(int i) {
		return i >= 0 && i < customMessages.length && customMessages[i] != null ? customMessages[i] : "";
	}

	/** @return whether the given row is suppressed by the block or the preset. */
	public boolean isRowHidden(int i) {
		return i >= 0 && i < rowHidden.length && rowHidden[i];
	}

	/** @return the block position as {@code [x, y, z]}. */
	public int[] blockPos() {
		return new int[]{blockPos.getX(), blockPos.getY(), blockPos.getZ()};
	}

	/** @return the platform ids this PIDS is filtered to; empty means "nearest". */
	public long[] targetPlatformIds() {
		final long[] result = new long[platformIds.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = platformIds.get(i);
		}
		return result;
	}

	/** @return {@code true} when the block auto-detects its platform rather than being filtered. */
	public boolean isPlatformAutoDetected() {
		return platformIds.isEmpty();
	}

	/** @return the name of the station the panel serves, or an empty string. */
	public String stationName() {
		final Station station = PIDSData.stationOf(primaryPlatformId());
		return station == null || station.name == null ? "" : station.name;
	}

	private long primaryPlatformId() {
		return platformIds.isEmpty() ? PIDSData.closestPlatformId(null, blockPos) : platformIds.get(0);
	}

	// ==================================================================
	// Arrival list
	// ==================================================================

	/** {@code pids.arrivals()} — {@code get(i)} is null past the end, exactly as in JCM 2.x. */
	public static class Arrivals {
		private final List<Arrival> arrivals;

		Arrivals(List<ScheduleEntry> scheduleList) {
			this.arrivals = new ArrayList<>();
			for (ScheduleEntry entry : scheduleList) {
				this.arrivals.add(new Arrival(entry));
			}
		}

		public Arrival get(int i) {
			return i >= 0 && i < arrivals.size() ? arrivals.get(i) : null;
		}

		public int size() {
			return arrivals.size();
		}

		/** @return {@code true} when the upcoming trains differ in length. */
		public boolean mixedCarLength() {
			int first = -1;
			for (Arrival arrival : arrivals) {
				if (first < 0) {
					first = arrival.carCount();
				} else if (arrival.carCount() != first) {
					return true;
				}
			}
			return false;
		}

		/** Convenience for scripts that want to iterate without index bookkeeping. */
		public Arrival[] toArray() {
			return arrivals.toArray(new Arrival[0]);
		}
	}

	// ==================================================================
	// One arrival
	// ==================================================================

	/**
	 * One upcoming train.
	 *
	 * <p>MTR 4's {@code ArrivalResponse} already carries destination, route number and
	 * circular state; MTR 3's {@link ScheduleEntry} only carries ids and a time, so the rest
	 * is resolved through {@link PIDSData}.</p>
	 */
	public static class Arrival {

		private final ScheduleEntry entry;

		Arrival(ScheduleEntry entry) {
			this.entry = entry;
		}

		public long arrivalTime() {
			return entry.arrivalMillis;
		}

		public boolean arrived() {
			return arrivalTime() <= System.currentTimeMillis();
		}

		public String destination() {
			return PIDSData.destination(entry);
		}

		public int carCount() {
			return entry.trainCars;
		}

		public long routeId() {
			return entry.routeId;
		}

		public String routeName() {
			return PIDSData.routeName(entry);
		}

		public int routeColor() {
			return PIDSData.routeColor(entry, 0);
		}

		/**
		 * @return the light-rail route number, or an empty string when the route has none.
		 * MTR 4 exposes this on the arrival itself; on MTR 3 it lives on the route.
		 */
		public String routeNumber() {
			final Route route = PIDSData.route(entry.routeId);
			if (route == null || route.lightRailRouteNumber == null) {
				return "";
			}
			return route.lightRailRouteNumber;
		}

		/**
		 * @return the circular state as a string so scripts can compare it directly.
		 *
		 * <p>MTR 4 spells the anticlockwise value {@code ANTI_CLOCKWISE} while MTR 3 uses
		 * {@code ANTICLOCKWISE}; the JCM 2.x spelling is returned here so scripts written
		 * against JCM 2.x keep working.</p>
		 */
		public String circularState() {
			final Route route = PIDSData.route(entry.routeId);
			if (route == null || route.circularState == null) {
				return "NONE";
			}
			final String name = route.circularState.name();
			return "ANTICLOCKWISE".equals(name) ? "ANTI_CLOCKWISE" : name;
		}

		public long platformId() {
			final Platform platform = platform();
			return platform == null ? 0L : platform.id;
		}

		public String platformName() {
			final Platform platform = platform();
			return platform == null || platform.name == null ? "" : platform.name;
		}

		/** @return the destination of the stop this train is heading to, when resolvable. */
		public int currentStationIndex() {
			return entry.currentStationIndex;
		}

		private Platform platform() {
			return PIDSData.platform(PIDSData.platformIdOf(entry));
		}
	}
}
