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

	/**
	 * @return {@code true}. MTR 4 distinguishes the "key" half of a two-block PIDS; MTR 3
	 * drives both halves from one block entity, so from a script's point of view it always is.
	 */
	public boolean isKeyBlock() {
		return true;
	}

	/**
	 * @return {@code false}. MTR 3 has no per-preset platform-number toggle on the block; the
	 * RV PIDS exposes one, but it is not reachable from here.
	 */
	public boolean isPlatformNumberHidden() {
		return false;
	}

	/** @return the station the panel serves, or {@code null} when it cannot be resolved. */
	public StationInfo station() {
		final Station station = PIDSData.stationOf(primaryPlatformId());
		return station == null ? null : new StationInfo(station);
	}

	/** Minimal station view, so JCM 2.x scripts can read a name without MTR 4's Station type. */
	public static class StationInfo {
		private final Station station;

		StationInfo(Station station) {
			this.station = station;
		}

		public String name() {
			return station.name == null ? "" : station.name;
		}

		/** MTR 4 spells this {@code getName()}; both work here. */
		public String getName() {
			return name();
		}

		public long id() {
			return station.id;
		}

		public int zone() {
			return station.zone;
		}
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

	/**
	 * {@code pids.arrivals()} — {@code get(i)} never returns {@code null}.
	 *
	 * <p>JCM 2.x documents {@code get(i)} as nullable past the end of the list, and its own
	 * {@code pids_1a.js} guards against it. Community presets generally do not: the
	 * <em>HZYMTR CRT Pids</em> pack computes
	 * {@code Math.ceil((pids.arrivals().get(1).arrivalTime() - Date.now()) / 60000)} on its
	 * second line, so a platform with fewer than two upcoming trains threw
	 * "Cannot call method arrivalTime of null", aborted {@code render()} before it had drawn
	 * anything, and left the whole panel blank.</p>
	 *
	 * <p>Returning a placeholder instead keeps such a preset working while changing nothing
	 * for a platform that does have the trains. The placeholder reports itself through
	 * {@link Arrival#isValid()} for scripts that want to tell the difference.</p>
	 */
	public static class Arrivals {
		private final List<Arrival> arrivals;

		Arrivals(List<ScheduleEntry> scheduleList) {
			this.arrivals = new ArrayList<>();
			for (ScheduleEntry entry : scheduleList) {
				this.arrivals.add(new Arrival(entry));
			}
		}

		public Arrival get(int i) {
			return i >= 0 && i < arrivals.size() ? arrivals.get(i) : Arrival.absent();
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

		/** @return the distinct platforms the listed arrivals call at, in arrival order. */
		public java.util.List<PlatformInfo> platforms() {
			final java.util.List<PlatformInfo> result = new ArrayList<>();
			final java.util.Set<Long> seen = new java.util.HashSet<>();
			for (Arrival arrival : arrivals) {
				final long platformId = arrival.platformId();
				if (platformId == 0 || !seen.add(platformId)) {
					continue;
				}
				final PlatformInfo info = arrival.platform();
				if (info != null) {
					result.add(info);
				}
			}
			return result;
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

		/** Shared placeholder returned for an index past the end of the list. */
		private static final Arrival ABSENT = new Arrival(null);

		private final ScheduleEntry entry;

		Arrival(ScheduleEntry entry) {
			this.entry = entry;
		}

		/** @return the placeholder used when there is no such train. */
		static Arrival absent() {
			return ABSENT;
		}

		/**
		 * @return {@code false} for the placeholder, {@code true} for a real arrival. Scripts
		 * that want to skip empty rows can test this instead of comparing against null.
		 */
		public boolean isValid() {
			return entry != null;
		}

		public long arrivalTime() {
			/* An absent train reports "now", so a preset that renders the row anyway says
			   "arriving" rather than showing a train scheduled in 1970. */
			return entry == null ? System.currentTimeMillis() : entry.arrivalMillis;
		}

		public boolean arrived() {
			return entry == null || arrivalTime() <= System.currentTimeMillis();
		}

		public String destination() {
			return entry == null ? "" : PIDSData.destination(entry);
		}

		public int carCount() {
			return entry == null ? 0 : entry.trainCars;
		}

		public long routeId() {
			return entry == null ? 0L : entry.routeId;
		}

		public String routeName() {
			return entry == null ? "" : PIDSData.routeName(entry);
		}

		public int routeColor() {
			return entry == null ? 0 : PIDSData.routeColor(entry, 0);
		}

		/**
		 * @return the light-rail route number, or an empty string when the route has none.
		 * MTR 4 exposes this on the arrival itself; on MTR 3 it lives on the route.
		 */
		public String routeNumber() {
			final Route route = entry == null ? null : PIDSData.route(entry.routeId);
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
			final Route route = entry == null ? null : PIDSData.route(entry.routeId);
			if (route == null || route.circularState == null) {
				return "NONE";
			}
			final String name = route.circularState.name();
			return "ANTICLOCKWISE".equals(name) ? "ANTI_CLOCKWISE" : name;
		}

		public long platformId() {
			final Platform platform = platformRef();
			return platform == null ? 0L : platform.id;
		}

		public String platformName() {
			final Platform platform = platformRef();
			return platform == null || platform.name == null ? "" : platform.name;
		}

		/** @return the destination of the stop this train is heading to, when resolvable. */
		public int currentStationIndex() {
			return entry == null ? 0 : entry.currentStationIndex;
		}

		/**
		 * @return the arrival time. MTR 3's {@code ScheduleEntry} carries a single timestamp for
		 * the stop, so there is no separate departure time to report; JCM 2.x scripts that read
		 * this get the stop time rather than an exception.
		 */
		public long departureTime() {
			return arrivalTime();
		}

		public boolean departed() {
			return departureTime() <= System.currentTimeMillis();
		}

		/** @return {@code 0}; MTR 3 does not expose schedule deviation to the client. */
		public long deviation() {
			return 0L;
		}

		/** @return {@code false}; MTR 3 does not flag whether a schedule is realtime. */
		public boolean realtime() {
			return false;
		}

		/** @return {@code 0}; MTR 3 carries no departure index on a schedule entry. */
		public long departureIndex() {
			return 0L;
		}

		/**
		 * @return {@code false}. MTR 4 marks the final stop of a run; MTR 3 does not expose it,
		 * so reporting "not terminating" keeps scripts on their normal display path.
		 */
		public boolean terminating() {
			return false;
		}

		/** @return the platform as a small view, or {@code null} when it cannot be resolved. */
		public PlatformInfo platform() {
			final Platform platform = PIDSData.platform(PIDSData.platformIdOf(entry));
			return platform == null ? null : new PlatformInfo(platform);
		}

		/** @return an empty list; MTR 3 does not stream per-car details to the client. */
		public java.util.List<Object> cars() {
			return java.util.Collections.emptyList();
		}

		private Platform platformRef() {
			return entry == null ? null : PIDSData.platform(PIDSData.platformIdOf(entry));
		}
	}

	/** Minimal platform view, so JCM 2.x scripts can read a name without MTR 4's Platform type. */
	public static class PlatformInfo {
		private final Platform platform;

		PlatformInfo(Platform platform) {
			this.platform = platform;
		}

		public String name() {
			return platform.name == null ? "" : platform.name;
		}

		/** MTR 4 spells this {@code getName()}; both work here. */
		public String getName() {
			return name();
		}

		public long id() {
			return platform.id;
		}
	}
}
