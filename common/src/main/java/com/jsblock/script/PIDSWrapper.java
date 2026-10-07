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
	/** Whether this block is the panel's key half; see {@link #isKeyBlock()}. */
	private final boolean keyBlock;
	/** Whether the block has its platform-number display switched off. */
	private final boolean platformNumberHidden;
	/** Whether {@code arrivals().get(i)} hands out a placeholder past the end; see {@link Arrivals}. */
	private final boolean lenientArrivals;

	/** Convenience for callers with no panel geometry to report; see the full constructor. */
	public PIDSWrapper(String type, int rows, int width, int height, BlockPos blockPos,
					   List<Long> platformIds, String[] customMessages, boolean[] rowHidden,
					   List<ScheduleEntry> scheduleList) {
		this(type, rows, width, height, blockPos, platformIds, customMessages, rowHidden, scheduleList, true, false);
	}

	public PIDSWrapper(String type, int rows, int width, int height, BlockPos blockPos,
					   List<Long> platformIds, String[] customMessages, boolean[] rowHidden,
					   List<ScheduleEntry> scheduleList, boolean keyBlock, boolean platformNumberHidden) {
		this(type, rows, width, height, blockPos, platformIds, customMessages, rowHidden, scheduleList,
				keyBlock, platformNumberHidden, false);
	}

	private PIDSWrapper(String type, int rows, int width, int height, BlockPos blockPos,
						List<Long> platformIds, String[] customMessages, boolean[] rowHidden,
						List<ScheduleEntry> scheduleList, boolean keyBlock, boolean platformNumberHidden,
						boolean lenientArrivals) {
		this.type = type;
		this.rows = rows;
		this.width = width;
		this.height = height;
		this.blockPos = blockPos;
		this.platformIds = platformIds == null ? new ArrayList<>() : new ArrayList<>(platformIds);
		this.customMessages = customMessages == null ? new String[0] : customMessages;
		this.rowHidden = rowHidden == null ? new boolean[0] : rowHidden;
		this.scheduleList = scheduleList == null ? new ArrayList<>() : new ArrayList<>(scheduleList);
		this.arrivals = new Arrivals(this.scheduleList, lenientArrivals);
		this.keyBlock = keyBlock;
		this.platformNumberHidden = platformNumberHidden;
		this.lenientArrivals = lenientArrivals;
	}

	/**
	 * @return a copy of this wrapper whose {@code arrivals().get(i)} returns a placeholder past
	 * the end instead of {@code null}.
	 *
	 * <p>Only handed to a script that has already proved it cannot cope with {@code null} — one
	 * that threw when it indexed past the end. See
	 * {@link ScriptEngine.Program#adoptLenientArrivals()}.</p>
	 */
	public PIDSWrapper withLenientArrivals() {
		return new PIDSWrapper(type, rows, width, height, blockPos, platformIds, customMessages,
				rowHidden, scheduleList, keyBlock, platformNumberHidden, true);
	}

	/** @return whether {@code arrivals().get(i)} hands out a placeholder past the end. */
	public boolean isLenientArrivals() {
		return lenientArrivals;
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
	 * {@code true} for the key half of a multi-block PIDS, {@code false} for the other one.
	 *
	 * <p>JCM 2.x's {@code PIDSBlockEntity.isKeyBlock()} is exactly
	 * {@code FACING == NORTH || FACING == EAST}, and its block code agrees: the
	 * {@code SOUTH}/{@code WEST} half is the one that reaches its companion with
	 * {@code pos.relative(FACING)}. A double-sided PIDS is therefore one panel drawn from two
	 * blocks, and presets use this flag to decide which half paints — {@code st_ql.js}, the
	 * 1A "station display" preset, draws on the key half when the platform number is shown and
	 * on the other half when it is hidden.</p>
	 *
	 * <p>This used to be hard-coded {@code true}, which made both halves paint the same
	 * picture one block apart, and made the hidden-platform-number branch paint nothing at
	 * all.</p>
	 */
	public boolean isKeyBlock() {
		return keyBlock;
	}

	/**
	 * @return whether the block's platform-number display is switched off.
	 *
	 * <p>JCM 2.x stores this per PIDS block entity. On MTR 3 the RV PIDS exposes the flag and
	 * the others do not, so a non-RV panel always reports {@code false}.</p>
	 */
	public boolean isPlatformNumberHidden() {
		return platformNumberHidden;
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

	/**
	 * The platform this panel is standing at.
	 *
	 * <p>MTR's own answer, from {@code IPIDS.TileEntityPIDS#getPlatformId}: the platform closest
	 * to the block, with the block entity's {@code platformIds} not consulted at all.</p>
	 *
	 * <p>This used to answer {@code platformIds.get(0)} whenever that set was non-empty, and the
	 * set is a <b>{@code Set<Long>}</b> — copying it into a list gives hash order, not anything
	 * meaningful. For a panel that serves several platforms the id that happened to land first
	 * often belongs to a station at the other end of the line, so {@code pids.station()} named
	 * that station instead of the one the panel is at. HKR's route-map strip starts its three
	 * stops from the current station, so it then drew the line's opening stations — and the
	 * platform number, the "arriving at this station" wording and anything else keyed off the
	 * current station were wrong in the same way.</p>
	 *
	 * <p>The set is still the fallback for the case MTR cannot answer: no platform near enough
	 * for {@code getClosePlatformId} to pick one.</p>
	 */
	private long primaryPlatformId() {
		final long closest = PIDSData.closestPlatformId(blockPos);
		if (closest != 0) {
			return closest;
		}
		return platformIds.isEmpty() ? 0 : platformIds.get(0);
	}

	// ==================================================================
	// Arrival list
	// ==================================================================

	/**
	 * {@code pids.arrivals()} — {@code get(i)} returns {@code null} past the end of the list.
	 *
	 * <p>That is JCM 2.x's own contract: {@code ArrivalsWrapper.get} is
	 * {@code i >= arrivals.size() ? null : ...}, and its {@code pids_1a.js} guards with
	 * {@code if (arrival == null) return}. Presets rely on it to leave rows blank — HKR's
	 * {@code drawFourArrivals} loops over four rows and draws only
	 * {@code if (train)}.</p>
	 *
	 * <p>This port used to return a placeholder <em>object</em> instead, so that presets which
	 * index past the end without checking — the CRT pack computes
	 * {@code pids.arrivals().get(1).arrivalTime()} on its second line — would not throw. That
	 * leniency cost more than it bought, because it is invisible from the preset's side:
	 * {@code if (train)} became true for an empty slot, so every guarded preset drew a phantom
	 * row. On HKR's board the phantom row went through {@code isNonPassenger}, which reports
	 * "no route" for the placeholder, and printed the "not in service" wording reserved for a
	 * genuinely empty train; its ETA then read "1 min" for ever, because the placeholder
	 * reported an arrival time of <em>now</em>, which {@code Math.ceil(0 / 60)} rounds up to
	 * one minute — and that text cycles languages every 60 ticks, which is the flicker that
	 * was reported next to it.</p>
	 *
	 * <p>A preset that indexes past the end without a guard throws, exactly as it would in
	 * JCM 2.x; the engine reports it once and falls back to the preset's background. If that
	 * throw is the only thing standing between the preset and a working board, the engine
	 * retries the frame with {@link PIDSWrapper#withLenientArrivals()} and keeps that choice
	 * for the panel — a resource pack cannot be edited from here, and a board showing a
	 * placeholder is better than a board showing nothing. Guarded presets never get there and
	 * keep the strict, correct behaviour.</p>
	 */
	public static class Arrivals {
		private final List<Arrival> arrivals;
		/** Whether an index past the end yields the placeholder rather than {@code null}. */
		private final boolean lenient;

		Arrivals(List<ScheduleEntry> scheduleList, boolean lenient) {
			this.arrivals = new ArrayList<>();
			for (ScheduleEntry entry : scheduleList) {
				this.arrivals.add(new Arrival(entry));
			}
			this.lenient = lenient;
		}

		/**
		 * @return the arrival at {@code i}; {@code null} when there is no such train, unless
		 * this wrapper is lenient, in which case a placeholder that answers every accessor
		 */
		public Arrival get(int i) {
			if (i >= 0 && i < arrivals.size()) {
				return arrivals.get(i);
			}
			return lenient ? Arrival.absent() : null;
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

		/**
		 * The object a <em>lenient</em> wrapper hands out past the end of the list.
		 *
		 * <p>It is deliberately not a real train: it reports the current time so a preset that
		 * renders the row anyway says "arriving" rather than a train scheduled in 1970, it has
		 * no route, and its destination is empty. That is also why the strict behaviour has to
		 * stay the default — a guarded preset cannot tell this object from a real train except
		 * by the empty fields, and HKR's board turned it into a row of "not in service".</p>
		 */
		private static final Arrival ABSENT = new Arrival(null);

		private final ScheduleEntry entry;

		Arrival(ScheduleEntry entry) {
			this.entry = entry;
		}

		/** @return the placeholder handed out by a lenient wrapper */
		static Arrival absent() {
			return ABSENT;
		}

		public long arrivalTime() {
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
		 * {@code arrival.route()} — the service this train is running, for route-map views.
		 *
		 * <p>HKR's presets draw the calling pattern with
		 * {@code route.getPlatforms().get(i).getStationName()}, so this hands back a wrapper
		 * rather than MTR 3's {@code Route}: a route stores platform ids only, and the station
		 * names have to be resolved through each platform.</p>
		 *
		 * @return the wrapper, or {@code null} when the route cannot be resolved — which is
		 * also what {@code pids.arrivals().get(i)} reports for a row with no train
		 */
		public RouteInfo route() {
			return entry == null ? null : new RouteInfo(PIDSData.route(entry.routeId));
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

	/**
	 * {@code route} view handed to scripts as {@code arrival.route()}.
	 *
	 * <p>HKR's route-map presets walk {@code getPlatforms()} in order and print each stop's
	 * station name, so the list has to resolve names the way MTR 3 stores them: a route holds
	 * platform ids, and the name lives on the station that platform belongs to.</p>
	 */
	public static class RouteInfo {
		private final Route route;

		RouteInfo(Route route) {
			this.route = route;
		}

		/** {@code route.getPlatforms()} — the stops this service calls at, in order. */
		public RouteStopList getPlatforms() {
			return new RouteStopList(route);
		}

		/** {@code route.getName()} — MTR 4 spells it this way, so both are offered. */
		public String getName() {
			return route == null || route.name == null ? "" : route.name;
		}

		/** @return the number of stops, or 0 when the route could not be resolved. */
		public int getPlatformCount() {
			return route == null || route.platformIds == null ? 0 : route.platformIds.size();
		}
	}

	/** The platform list of a {@link RouteInfo}, addressed as {@code get(i)} like JCM 2.x's. */
	public static class RouteStopList {
		private final Route route;

		RouteStopList(Route route) {
			this.route = route;
		}

		public int size() {
			return route == null || route.platformIds == null ? 0 : route.platformIds.size();
		}

		/**
		 * @return the stop at {@code i}; past the end this is an unnamed stop rather than null,
		 * because HKR's loops iterate to the list's own size and a null would abort the frame
		 */
		public RouteStopInfo get(int i) {
			if (route == null || route.platformIds == null || i < 0 || i >= route.platformIds.size()) {
				return new RouteStopInfo(0L);
			}
			return new RouteStopInfo(route.platformIds.get(i).platformId);
		}
	}

	/** One stop of a route, as read by {@code getPlatforms().get(i)}. */
	public static class RouteStopInfo {
		private final long platformId;

		RouteStopInfo(long platformId) {
			this.platformId = platformId;
		}

		/** {@code getStationName()} — MTR 4's spelling; this is what HKR prints. */
		public String getStationName() {
			final Station station = PIDSData.stationOf(platformId);
			return station == null || station.name == null ? "" : station.name;
		}

		public long getPlatformId() {
			return platformId;
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
