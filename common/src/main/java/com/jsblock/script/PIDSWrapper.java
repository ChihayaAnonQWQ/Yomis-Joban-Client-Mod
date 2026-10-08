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
	/**
	 * The scope the current script call runs in, published by the engine for its duration.
	 *
	 * <p>Thread-local because scripts run on their own execution threads, and the value is only
	 * meaningful inside a create/render/dispose call.</p>
	 */
	private static final ThreadLocal<org.mozilla.javascript.Scriptable> CURRENT_SCOPE = new ThreadLocal<>();

	/** Publishes the scope for the call the engine is about to make. */
	public static void enterScriptScope(org.mozilla.javascript.Scriptable scope) {
		CURRENT_SCOPE.set(scope);
	}

	/** Takes it back down again, whether the call succeeded or threw. */
	public static void exitScriptScope() {
		CURRENT_SCOPE.remove();
	}

	/**
	 * A JavaScript array holding the given elements.
	 *
	 * <p>{@code cx.newArray} is the only construction Rhino gives a prototype to. An array built
	 * here with {@code new NativeArray(...)} has neither prototype nor parent scope, so
	 * {@code .map()}, {@code .slice()} and {@code .findIndex()} -- which is exactly what the packs
	 * call on the result -- are not reachable, and Rhino reports that as "Cannot find default value
	 * for object", an error that names nothing useful. Reproduced offline in work/probe/rhino-probe.</p>
	 */
	static org.mozilla.javascript.NativeArray scriptArray(java.util.List<Object> elements) {
		final Object[] array = elements.toArray();
		final org.mozilla.javascript.Scriptable scope = CURRENT_SCOPE.get();
		final org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.getCurrentContext();
		if (scope != null && cx != null) {
			final Object created = cx.newArray(scope, array);
			if (created instanceof org.mozilla.javascript.NativeArray) {
				return (org.mozilla.javascript.NativeArray) created;
			}
		}
		return new org.mozilla.javascript.NativeArray(array);
	}

	public PIDSWrapper withLenientArrivals() {
		final PIDSWrapper copy = new PIDSWrapper(type, rows, width, height, blockPos, platformIds, customMessages,
				rowHidden, scheduleList, keyBlock, platformNumberHidden, true);
		return copy;
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

	/**
	 * @return the block's position as a {@code Vector3f}.
	 *
	 * <p>JCM 2.x returns a vector whose coordinates are read as methods -- {@code pids.blockPos().x()}
	 * is what packs actually write -- so returning an int array here made every such script die with
	 * {@code x is not a function}. The docs call the type {@code Vector3f} and list the same
	 * accessors, so the vector is the shape to match.</p>
	 */
	public ScriptMath.Vector3f blockPos() {
		return new ScriptMath.Vector3f(blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	/** @return the platform ids this PIDS is filtered to; empty means "nearest". */
	/**
	 * JCM 2.x's name for the same thing.
	 *
	 * <p>Their wrapper calls it {@code getTargetPlatformIds()} and hands back a
	 * {@code LongImmutableList}; this returns the plain array the rest of the port passes around.
	 * No preset in the corpus calls it under either name -- a panel's platforms are read through the
	 * arrivals -- but a pack written against their API can reach for it, and reaching for a method
	 * that is not there ends the script.</p>
	 */
	public long[] getTargetPlatformIds() {
		return targetPlatformIds();
	}

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

	/**
	 * Minimal station view, so JCM 2.x scripts can read a name without MTR 4's Station type.
	 *
	 * <h2>Why these are fields and not only methods</h2>
	 * <p>MTR 4 gives {@code Station} public fields, so that is how every preset reads it:
	 * {@code "" + pids.station().name}. Rhino answers a property lookup on a <b>method</b> named
	 * {@code name} with the bound function object rather than its return value, so
	 * {@code currentStation.name} came out as {@code "function name() {…}"} and HKR's route map
	 * never matched the panel's own station — it fell back to index 0 and drew the line's opening
	 * stations. The accessor methods stay for Java callers.</p>
	 */
	public static class StationInfo {
		/** {@code station.name} — MTR 4's field spelling, and what presets actually read. */
		public final String name;
		/** {@code station.id}. */
		public final long id;
		/** {@code station.zone}. */
		public final int zone;
		/**
		 * {@code station.color} -- the station area's RGB colour.
		 *
		 * <p>MTR 4 spells it as a field and offers {@code getColor()} beside it; packs use the
		 * method and feed the result straight to {@code .color(...)}.</p>
		 */
		public final int color;

		StationInfo(Station station) {
			this.name = station.name == null ? "" : station.name;
			this.id = station.id;
			this.zone = station.zone;
			this.color = station.color;
		}

		/** MTR 4 spells this {@code getName()}; both work here. */
		public String getName() {
			return name;
		}

		/** {@code Station.getColor()} -- the station colour as an RGB int. */
		public int getColor() {
			return color;
		}

		/** {@code Station.getColorHex()} -- the same colour as {@code RRGGBB}. */
		public String getColorHex() {
			return String.format("%06X", color & 0xFFFFFF);
		}

		/** {@code Station.getId()}. */
		public long getId() {
			return id;
		}

		/** {@code Station.getHexId()} -- the id in hexadecimal, as signage prints it. */
		public String getHexId() {
			return Long.toHexString(id);
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

		/**
		 * Convenience for scripts that want to iterate without index bookkeeping.
		 *
		 * <p>A JavaScript array, not an {@code Arrival[]}: a pack will follow this with
		 * {@code .map()} or the like, and Rhino's wrapper around a Java array has indexing but none
		 * of the Array prototype's methods.</p>
		 */
		public org.mozilla.javascript.NativeArray toArray() {
			final Arrival[] source = arrivals.toArray(new Arrival[0]);
			final java.util.List<Object> wrapped = new java.util.ArrayList<>(source.length);
			for (Arrival value : source) {
				wrapped.add(toScriptObject(value));
			}
			return scriptArray(wrapped);
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
		 * @return when the train leaves this stop
		 *
		 * <p>MTR 3's {@code ScheduleEntry} carries one timestamp for the stop, so there is no
		 * departure time to report directly — but the stop's dwell time is on the platform, and
		 * arrival plus dwell is what a preset means by "still standing here". HKR's door-closing
		 * window reads exactly this: {@code etaDepart > 0 && etaDepart <= 10}. Returning the
		 * arrival time instead made that window empty (it is only positive while the train is
		 * still approaching, where the arriving branch has already claimed the frame), so the
		 * door-closing display could never appear.</p>
		 *
		 * <p><b>The dwell time is in half-seconds, not seconds.</b> MTR's own conversion is
		 * {@code Train#getDwellTimeTicks}: {@code PathData.dwellTime * 10} ticks, and ten ticks is
		 * half a second. A platform set to 20 seconds therefore reports 40 here; treating that as
		 * seconds doubled every departure. MTR's door-close lead comes out of the same unit, as
		 * {@code min(64, dwellTicks / 2 - 20)} ticks before departure.</p>
		 */
		public long departureTime() {
			final Platform platform = platformRef();
			final int halfSeconds = platform == null ? 0 : platform.getDwellTime();
			return arrivalTime() + halfSeconds / 2 * 1000L;
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

		/** {@code route.name} — a field in MTR 4, and how the LCD packs read it. */
		public final String name;

		RouteInfo(Route route) {
			this.route = route;
			this.name = route == null || route.name == null ? "" : route.name;
		}

		/** {@code route.getPlatforms()} — the stops this service calls at, in order. */
		public RouteStopList getPlatforms() {
			return new RouteStopList(route);
		}

		/** {@code route.getName()} — MTR 4 spells it this way, so both are offered. */
		public String getName() {
			return name;
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
				return new RouteStopInfo(0L, route);
			}
			return new RouteStopInfo(route.platformIds.get(i).platformId, route);
		}

		/**
		 * @return every stop as a JavaScript array.
		 *
		 * <p>A real one, not a {@code RouteStopInfo[]}. Two presets in one pack fail on this line --
		 * {@code db_big.js#111} and {@code sydney.js#67} -- and the Sydney one carries on with
		 * {@code .map()}, {@code .slice()} and {@code .findIndex()}, none of which exist on the
		 * array Rhino builds around a Java array.</p>
		 */
		public org.mozilla.javascript.NativeArray toArray() {
			final int count = size();
			final java.util.List<Object> stops = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				stops.add(toScriptObject(get(i)));
			}
			final org.mozilla.javascript.NativeArray array = scriptArray(stops);
			return array;
		}
	}

	/** One stop of a route, as read by {@code getPlatforms().get(i)}. */
	public static class RouteStopInfo {
		private final long platformId;

		/**
		 * {@code stop.station.name} — MTR 4 carries {@code station} as a <b>field</b> on a route
		 * stop, and the LCD packs read it exactly that way ({@code xl[i].station.name}). Rhino
		 * hands back a bound function for a method of the same name, so a field is the only
		 * spelling that works.
		 */
		public final StationInfo station;
		/** {@code stop.route.name} — MTR 4's field again; read by the same packs. */
		public final RouteInfo route;

		/**
		 * {@code stop.stationName} -- a String field, not the {@link #getStationName()} method.
		 *
		 * <p>Thirty-three scripts in World PIDS-Pack write
		 * {@code getPlatforms().toArray().map(platform => platform.stationName)} and then treat the
		 * result as a string: {@code .normalize("NFC")}, {@code .replace(...)}, {@code .trim()}.
		 * Reading a property that exists only as a method does not yield undefined here -- Rhino
		 * fails the whole render with "Cannot find default value for object". A field is the spelling
		 * that works, the same reason {@link #station} and {@link #route} are fields.</p>
		 */
		public final String stationName;

		RouteStopInfo(long platformId, Route routeRef) {
			this.platformId = platformId;
			final Station resolved = PIDSData.stationOf(platformId);
			this.station = resolved == null ? null : new StationInfo(resolved);
			this.route = routeRef == null ? null : new RouteInfo(routeRef);
			this.stationName = this.station == null || this.station.name == null ? "" : this.station.name;
		}

		/** {@code getStationName()} — MTR 4's spelling; this is what HKR prints. */
		public String getStationName() {
			return station == null ? "" : station.name;
		}

		public long getPlatformId() {
			return platformId;
		}

		/**
		 * The class's default value, as far as Rhino is concerned.
		 *
		 * <p>{@code Cannot find default value for object} is what a script gets when the engine needs
		 * a primitive out of a Java object and cannot find one. A stop is a station name as far as
		 * any preset is concerned, so that is what it reduces to.</p>
		 */
		@Override
		public String toString() {
			return stationName;
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

	/**
	 * A route stop as a plain JavaScript object.
	 *
	 * <p>Building the array out of raw Java objects is what produces {@code Cannot find default
	 * value for object}: the script-visible array tries to reduce each element to something it can
	 * hold, and a {@code RouteStopInfo} is not one of those things. A NativeObject has exactly the
	 * properties it was given, so {@code platform.stationName} resolves the way thirty-three scripts
	 * in one pack expect, and so do the two other spellings packs use.</p>
	 */
	private static org.mozilla.javascript.NativeObject toScriptObject(RouteStopInfo stop) {
		final org.mozilla.javascript.NativeObject object = new org.mozilla.javascript.NativeObject();
		object.defineProperty("stationName", safeString(stop.stationName), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("platformId", stop.getPlatformId(), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("stationNameRaw", safeString(stop.getStationName()), org.mozilla.javascript.ScriptableObject.READONLY);
		if (stop.station != null) {
			final org.mozilla.javascript.NativeObject station = new org.mozilla.javascript.NativeObject();
			station.defineProperty("name", safeString(stop.station.name), org.mozilla.javascript.ScriptableObject.READONLY);
			station.defineProperty("id", stop.station.id, org.mozilla.javascript.ScriptableObject.READONLY);
			object.defineProperty("station", station, org.mozilla.javascript.ScriptableObject.READONLY);
		}
		if (stop.route != null) {
			final org.mozilla.javascript.NativeObject route = new org.mozilla.javascript.NativeObject();
			route.defineProperty("name", safeString(stop.route.getName()), org.mozilla.javascript.ScriptableObject.READONLY);
			object.defineProperty("route", route, org.mozilla.javascript.ScriptableObject.READONLY);
		}
		return object;
	}

	/**
	 * An arrival as a plain JavaScript object, carrying the accessors the packs call.
	 *
	 * <p>Only the handful a preset script actually reaches for; anything absent reads as
	 * undefined rather than aborting the render, which is the failure mode this exists to stop.</p>
	 */
	private static org.mozilla.javascript.NativeObject toScriptObject(Arrival arrival) {
		final org.mozilla.javascript.NativeObject object = new org.mozilla.javascript.NativeObject();
		object.defineProperty("destination", safeString(arrival.destination()), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("platformName", safeString(arrival.platformName()), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("routeName", safeString(arrival.routeName()), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("routeNumber", safeString(arrival.routeNumber()), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("routeColor", arrival.routeColor(), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("carCount", arrival.carCount(), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("arrivalTime", arrival.arrivalTime(), org.mozilla.javascript.ScriptableObject.READONLY);
		object.defineProperty("departureTime", arrival.departureTime(), org.mozilla.javascript.ScriptableObject.READONLY);
		return object;
	}


	/**
	 * Never hands a null to the script.
	 *
	 * <p>{@code defineProperty} with a null value is what "Cannot find default value for object"
	 * turned out to be: a station on a route can exist with an unset name, and the property is then
	 * null, which Rhino cannot reduce to anything. An empty string says the same thing and draws.</p>
	 */
	private static String safeString(String value) {
		return value == null ? "" : value;
	}


}
