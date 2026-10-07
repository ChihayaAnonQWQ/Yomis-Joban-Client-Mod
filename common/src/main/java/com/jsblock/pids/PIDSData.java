package com.jsblock.pids;

import mtr.client.ClientData;
import mtr.data.Platform;
import mtr.data.RailwayData;
import mtr.data.Route;
import mtr.data.ScheduleEntry;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;

/**
 * Thin read-only façade over MTR 3's client-side datasets, so PIDS components never
 * have to reach into {@code ClientData} directly.
 *
 * <p>On MTR 4 JCM reads the equivalent facts from {@code org.mtr.core.data.*} and
 * {@code org.mtr.mod.client.MinecraftClientData}; on MTR 3 the same information lives in
 * {@link ClientData#DATA_CACHE} (a {@code ClientCache}) and
 * {@link ClientData#SCHEDULES_FOR_PLATFORM}.</p>
 */
public final class PIDSData {

	private PIDSData() {
	}

	/** @return the platform nearest to {@code pos}, or {@code 0} when none is known. */
	public static long closestPlatformId(Level world, BlockPos pos) {
		if (world == null || pos == null) {
			return 0;
		}
		try {
			return RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
		} catch (Exception e) {
			return 0;
		}
	}

	/** @return every upcoming arrival advertised for {@code platformId}. */
	public static Set<ScheduleEntry> schedulesFor(long platformId) {
		final Set<ScheduleEntry> schedules = ClientData.SCHEDULES_FOR_PLATFORM.get(platformId);
		return schedules == null ? Collections.emptySet() : schedules;
	}

	/** @return the route with the given id, or {@code null}. */
	public static Route route(long routeId) {
		return ClientData.DATA_CACHE.routeIdMap.get(routeId);
	}

	/** @return the platform with the given id, or {@code null}. */
	public static Platform platform(long platformId) {
		return ClientData.DATA_CACHE.platformIdMap.get(platformId);
	}

	/** Set once the cache sizes have been reported, so the log is not flooded per frame. */
	private static boolean reportedCache;

	/**
	 * Reports the sizes of the client data caches a PIDS draws from, once.
	 *
	 * <p>Station names, destinations and route maps all resolve through
	 * {@code ClientData.DATA_CACHE}. When every one of them comes back empty at the same
	 * time, the question is whether the lookup keys are wrong or the cache itself never
	 * filled, and a screenshot cannot tell those apart. This prints the counts so it can.</p>
	 */
	public static void reportCacheOnce() {
		if (reportedCache) {
			return;
		}
		reportedCache = true;
		try {
			com.jsblock.Joban.LOGGER.info("[Joban Client] [PIDS] client data: routes={} platforms={} stations={} platformIdToStation={}",
					ClientData.DATA_CACHE.routeIdMap.size(),
					ClientData.DATA_CACHE.platformIdMap.size(),
					ClientData.STATIONS.size(),
					ClientData.DATA_CACHE.platformIdToStation.size());
		} catch (Throwable t) {
			com.jsblock.Joban.LOGGER.warn("[Joban Client] [PIDS] could not read the client data caches: {}", t.toString());
		}
	}

	/** @return the station the platform belongs to, or {@code null}. */
	public static Station stationOf(long platformId) {
		reportCacheOnce();
		return ClientData.DATA_CACHE.platformIdToStation.get(platformId);
	}

	/**
	 * Resolves the destination text a route shows when arriving at the stop described by
	 * a schedule entry.
	 *
	 * <p>MTR 3 exposes the same two-step rule MTR 4 does: a per-stop custom destination
	 * wins, otherwise {@link Route#getDestination(int)} is used. The {@code currentStationIndex}
	 * carried by {@link ScheduleEntry} is the index of the stop the train is <em>heading to</em>.</p>
	 *
	 * @return the destination, or an empty string when it cannot be resolved.
	 */
	public static String destination(ScheduleEntry entry) {
		if (entry == null) {
			return "";
		}
		final Route route = route(entry.routeId);
		if (route == null) {
			return "";
		}
		try {
			final String destination = route.getDestination(entry.currentStationIndex);
			return Route.destinationIsReset(destination) ? "" : destination;
		} catch (Exception e) {
			return "";
		}
	}

	/** @return the arrival's route name, or an empty string. */
	public static String routeName(ScheduleEntry entry) {
		final Route route = entry == null ? null : route(entry.routeId);
		return route == null || route.name == null ? "" : route.name;
	}

	/** @return the arrival's route colour as an ARGB value, or {@code 0}. */
	public static int routeColor(ScheduleEntry entry, int fallback) {
		final Route route = entry == null ? null : route(entry.routeId);
		return route == null ? fallback : route.color;
	}

	/**
	 * Resolves the platform an arrival is heading to.
	 *
	 * <p>MTR 4's {@code ArrivalResponse} carries its platform id directly. MTR 3's
	 * {@link ScheduleEntry} does not, but {@code currentStationIndex} is the index of the stop
	 * the train is heading to, so the platform is that entry of the route's platform list —
	 * the same platform the panel is announcing.</p>
	 *
	 * @return the platform id, or {@code 0} when it cannot be resolved
	 */
	public static long platformIdOf(ScheduleEntry entry) {
		final Route route = entry == null ? null : route(entry.routeId);
		if (route == null || route.platformIds == null) {
			return 0L;
		}
		final int index = entry.currentStationIndex;
		if (index < 0 || index >= route.platformIds.size()) {
			return 0L;
		}
		return route.platformIds.get(index).platformId;
	}
}
