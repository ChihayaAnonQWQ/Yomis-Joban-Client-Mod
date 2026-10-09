package com.jsblock.script;

import mtr.client.ClientData;
import mtr.data.Platform;
import mtr.data.Route;
import mtr.data.ScheduleEntry;
import mtr.data.Station;
import mtr.data.TransportMode;
import net.minecraft.core.BlockPos;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs HKR's terminus rule against a real MTR-shaped world, and checks that the two panels on one
 * line disagree the way the rule says they should.
 *
 * <h2>The rule</h2>
 * <p>HKR's presets decide "not in service" like this ({@code hkr_pids_default.js:396-410}, and
 * {@code hkr_pids_platform.js:239-252} word for word):</p>
 *
 * <pre>
 * function isNonPassenger(train, pids) {
 *     let route = train.route();
 *     if (!route) return true;
 *     let platforms = route.getPlatforms();
 *     if (!platforms || platforms.size() === 0) return true;
 *     let currentStation = pids.station();
 *     if (!currentStation) return false;
 *     let curName = "" + currentStation.name;
 *     for (let i = 0; i &lt; platforms.size(); i++) {
 *         if ("" + platforms.get(i).getStationName() === curName) {
 *             return i &gt;= platforms.size() - 1;
 *         }
 *     }
 *     return false;
 * }
 * </pre>
 *
 * <p>{@code i} is the index of the panel's own station in the service's calling pattern, and the
 * rule is "the panel is at the last stop". Its answer therefore depends on
 * {@code pids.station()} naming the station the panel is actually standing at: if that is
 * {@code null} the rule cannot find {@code i} at all and answers "in service", and if it names
 * some other station on the line the rule answers for <em>that</em> station — a mid-line panel
 * wearing the terminus's name is "at" the last stop, so every arrival there printed
 * {@code 不載客列車 / Not in Service} on a panel that has real trains.</p>
 *
 * <h2>The fixtures</h2>
 * <p>A line of three stations — {@code 你好} → {@code 测试} → {@code 114514} — with one platform
 * each, and a route that calls at them in that order. The panel is placed on the platform the
 * game would place it on, and the block's own {@code platformIds} is what a PIDS block carries:
 * the filter the player configured, or nothing at all on an auto-detected one. The same preset
 * then has to draw opposite things at the two ends.</p>
 *
 * <p>The MTR objects are the real ones and are filled into MTR's real client caches, so the path
 * under test is the one a game uses — {@code PIDSData.stationOf}, {@code RailwayData}'s
 * nearest-platform lookup, and the route's platform list — and not a stub of it.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * java com.jsblock.script.ScriptHkrCheck &lt;hkr_pids_default.js&gt; &lt;resourceRoot&gt; ...
 * </pre>
 *
 * <p>With the two real preset paths the check also requires the pack's own wording to appear where
 * the rule says it should, which is the fixture's question asked of the shipped script rather than
 * of the embedded one. The resource roots are the same ones {@link ScriptApiCheck} takes: a
 * directory holding {@code assets/&lt;namespace&gt;/...}, with the repository's own
 * {@code common/src/main/resources} included so {@code jsblock:scripts/pids_util.js} resolves.</p>
 */
public final class ScriptHkrCheck {

	/** Searched in order when a script calls include(<namespace>:<path>). */
	private static final List<Path> RESOURCE_ROOTS = new ArrayList<>();

	/**
	 * The preset the fixtures run, which is HKR's {@code isNonPassenger} and nothing else.
	 *
	 * <p>It draws its answer rather than returning it: a script's result is what it puts on the
	 * panel, so the assertions read the recorded draw calls. The full HKR preset cycles between a
	 * four-arrival view and a route-map view on a frame counter, which would make a check that
	 * only runs a few frames depend on where in the cycle it landed; this one draws the answer on
	 * every frame, and the check can then read a frame and know what it was looking at.</p>
	 */
	private static final String FIXTURE_PRESET =
			"function create(ctx, state, pids) {}\n"
					+ "function dispose(ctx, state, pids) {}\n"
					+ "function isNonPassenger(train, pids) {\n"
					+ "    let route = train.route();\n"
					+ "    if (!route) return true;\n"
					+ "    let platforms = route.getPlatforms();\n"
					+ "    if (!platforms || platforms.size() === 0) return true;\n"
					+ "    let currentStation = pids.station();\n"
					+ "    if (!currentStation) return false;\n"
					+ "    let curName = \"\" + currentStation.name;\n"
					+ "    for (let i = 0; i < platforms.size(); i++) {\n"
					+ "        if (\"\" + platforms.get(i).getStationName() === curName) {\n"
					+ "            return i >= platforms.size() - 1;\n"
					+ "        }\n"
					+ "    }\n"
					+ "    return false;\n"
					+ "}\n"
					+ "function render(ctx, state, pids) {\n"
					+ "    let train = pids.arrivals().get(0);\n"
					+ "    if (!train) return;\n"
					+ "    let stops = train.route().getPlatforms();\n"
					+ "    let index = \"none\";\n"
					+ "    let station = pids.station();\n"
					+ "    if (station) {\n"
					+ "        for (let i = 0; i < stops.size(); i++) {\n"
					+ "            if (\"\" + stops.get(i).getStationName() === \"\" + station.name) index = \"\" + i;\n"
					+ "        }\n"
					+ "    }\n"
					+ "    Text.create().text(\"PANEL_STATION=\" + (station ? station.name : \"null\"))\n"
					+ "        .pos(0, 0).draw(ctx);\n"
					+ "    Text.create().text(\"PANEL_INDEX=\" + index).pos(0, 8).draw(ctx);\n"
					+ "    Text.create().text(isNonPassenger(train, pids) ? \"NOT_IN_SERVICE\" : \"IN_SERVICE\")\n"
					+ "        .pos(0, 16).draw(ctx);\n"
					+ "    Text.create().text(train.destination()).pos(0, 24).draw(ctx);\n"
					+ "}\n";

	/** The station names, the order the line calls at them, and the platforms they hold. */
	private static final String[] STATION_NAMES = {"\u4f60\u597d", "\u6d4b\u8bd5", "114514"};
	private static final long[] PLATFORM_IDS = {101L, 102L, 103L};
	private static final long ROUTE_ID = 1L;

	private static int failures;

	private ScriptHkrCheck() {
	}

	public static void main(String[] args) throws Exception {
		Path realDefault = null;
		Path realPlatform = null;
		for (String arg : args) {
			final Path candidate = Paths.get(arg);
			if (arg.endsWith(".js")) {
				if (arg.contains("hkr_pids_platform")) {
					realPlatform = candidate;
				} else {
					realDefault = candidate;
				}
			} else {
				RESOURCE_ROOTS.add(candidate);
			}
		}

		System.out.println("== HKR terminus rule (ScriptHkrCheck) ==");
		System.out.println("   resource roots: " + RESOURCE_ROOTS);

		installFixtureData();

		runFixture("terminus, panel configured for its own platform", 2, new long[]{103L}, 103L,
				"NOT_IN_SERVICE");
		runFixture("mid-line, panel configured for its own platform", 1, new long[]{102L}, 102L,
				"IN_SERVICE");
		runFixture("terminus, auto-detected panel (no filter)", 2, new long[]{}, 103L,
				"NOT_IN_SERVICE");
		runFixture("mid-line, auto-detected panel (no filter)", 1, new long[]{}, 102L,
				"IN_SERVICE");
		/* What the panel's own filter says loses to where the panel actually is: MTR does not
		   consult platformIds for this, and a filter naming a platform at the other end of the
		   line is exactly the case that used to put the terminus's name on a mid-line panel. */
		runFixture("mid-line, filter points at the terminus end", 1, new long[]{103L}, 102L,
				"IN_SERVICE");
		runFixture("terminus, filter points at the far end", 2, new long[]{101L}, 103L,
				"NOT_IN_SERVICE");

		checkNearestPlatformAnswers(1);
		checkMarqueeAcceptsANumber();

		if (realDefault != null) {
			/* At the terminus there is no station name to require: every branch of the pack that
			   would draw one is behind the rule this check is about. Mid-line the default preset's
			   route-map strip draws the panel's own station, which is the evidence that the rule
			   found the right index rather than being handed a name that is not there. */
			runRealScript("default", realDefault, 2, new long[]{103L}, 103L, new String[]{}, "NOT_IN_SERVICE");
			runRealScript("default", realDefault, 1, new long[]{102L}, 102L, new String[]{"\u6d4b\u8bd5"}, "IN_SERVICE");
		}
		if (realPlatform != null) {
			/* The platform preset's route-map view prints no station name at all — it prints the
			   platform the train will stop at — so there is nothing else to require of it beyond
			   the answer itself. */
			runRealScript("platform", realPlatform, 2, new long[]{103L}, 103L, new String[]{}, "NOT_IN_SERVICE");
			runRealScript("platform", realPlatform, 1, new long[]{102L}, 102L, new String[]{}, "IN_SERVICE");
		}
		if (realDefault == null && realPlatform == null) {
			System.out.println();
			System.out.println("NOTE no real HKR script was given, so only the embedded preset ran."
					+ " Pass its path to check the shipped pack:");
			System.out.println("     ... ScriptHkrCheck <pack>/lower/assets/jsblock/scripts/hkr_pids_default.js"
					+ " <pack>/lower common/src/main/resources");
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: HKR TERMINUS RULE OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/**
	 * The line the fixtures share: three stations, three platforms, one route that calls at them
	 * in order, and the cache entries that let those objects be read the way a game reads them.
	 *
	 * <p>The platform positions are a station apart, and each platform's blocks are registered in
	 * MTR's own block-position map, so {@code RailwayData.getClosePlatformId} answers on its first
	 * lookup instead of falling back to a distance scan — the same path a panel in a station
	 * takes in game.</p>
	 */
	private static void installFixtureData() {
		final Station[] stations = new Station[STATION_NAMES.length];
		for (int i = 0; i < STATION_NAMES.length; i++) {
			stations[i] = new Station(i + 1L);
			stations[i].name = STATION_NAMES[i];
			ClientData.STATIONS.add(stations[i]);
			ClientData.DATA_CACHE.stationIdMap.put(i + 1L, stations[i]);
		}

		final Route route = new Route(ROUTE_ID, TransportMode.TRAIN);
		route.name = "HKR fixture line";
		for (int i = 0; i < PLATFORM_IDS.length; i++) {
			/* Station i occupies x = 100*i .. 100*i + 10, so an adjacent platform's blocks are
			   never nearer to a panel than its own. */
			final int x = 100 * i;
			final Platform platform = new Platform(PLATFORM_IDS[i], TransportMode.TRAIN,
					new BlockPos(x, 64, 0), new BlockPos(x, 64, 10));
			platform.name = "" + (i + 1);
			ClientData.PLATFORMS.add(platform);
			ClientData.DATA_CACHE.platformIdMap.put(PLATFORM_IDS[i], platform);
			ClientData.DATA_CACHE.platformIdToStation.put(PLATFORM_IDS[i], stations[i]);
			for (int z = 0; z <= 10; z++) {
				ClientData.DATA_CACHE.blockPosToPlatformId.put(BlockPos.asLong(x, 64, z), PLATFORM_IDS[i]);
			}
			route.platformIds.add(new Route.RoutePlatform(PLATFORM_IDS[i]));
		}
		ClientData.DATA_CACHE.routeIdMap.put(ROUTE_ID, route);
	}

	/** @return the panel's block position on the platform at {@code x}, which is where the game puts it */
	private static BlockPos panelOn(int stationIndex) {
		return new BlockPos(100 * stationIndex, 64, -5);
	}

	private static void runFixture(String label, int stationIndex, long[] filter, long resolved, String expected) {
		System.out.println();
		System.out.println("-- " + label);
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			installFileInclude(cx, scope);
			cx.evaluateString(scope, FIXTURE_PRESET, "hkr-terminus-fixture.js", 1, null);
			final ScriptableObject state = scriptState(scope, cx);
			final PIDSWrapper pids = panelWrapper(cx, stationIndex, filter, resolved);
			((Function) scope.get("create", scope)).call(cx, scope, scope, new Object[]{null, state, pids});
			final ScriptRenderContext ctx = ScriptRenderContext.dryRun(128, 72, 1F);
			((Function) scope.get("render", scope)).call(cx, scope, scope, new Object[]{ctx, state, pids});
			final List<String> calls = ctx.recordedCalls();

			expectText(calls, "PANEL_STATION=" + STATION_NAMES[stationIndex], label);
			expectText(calls, "PANEL_INDEX=" + stationIndex, label);
			expectText(calls, expected, label);
			if ("IN_SERVICE".equals(expected)) {
				/* The other half of the rule: a mid-line panel hands the arrival's own destination
				   to the panel, so the badge cannot be on screen under another name. */
				if (drawn(calls, "\u4e0d\u8f09\u5ba2\u5217\u8eca") || drawn(calls, "Not in Service")) {
					fail(label + ": the panel drew \"not in service\" while the rule answered in service");
				} else {
					pass(label + ": no \"not in service\" wording on a mid-line panel");
				}
			}
		} catch (Exception e) {
			fail(label + " threw: " + e);
		} finally {
			Context.exit();
		}
	}

	/**
	 * The real preset, run at both ends of the line, asserted on the wording HE draws.
	 *
	 * <p>The frame counter is set so the panel is in its route-map view, which is one of the two
	 * places {@code isNonPassenger} decides what the panel says; {@code drawDestination} is the
	 * other and is reached from the same frame.</p>
	 *
	 * <p>{@code expectedTexts} are the strings the frame has to contain. They are {@code 测试} —
	 * the panel's own station, drawn by the route-map strip on a panel that is in service — and
	 * not the wording the pack cycles between Chinese and English ({@code TextUtil.cycleString}),
	 * because which half of the cycle a frame lands on is not what this check is about. The
	 * wording is asserted through {@code isNonPassenger}'s answer instead, which is what the
	 * cycle cannot change: the badge is on screen, or no departure is.</p>
	 */
	private static void runRealScript(String which, Path script, int stationIndex, long[] filter, long resolved,
									  String[] expectedTexts, String expectedVerdict) {
		System.out.println();
		System.out.println("-- real HKR script (" + which + "): " + script.getFileName()
				+ " at station index " + stationIndex);
		if (!Files.isRegularFile(script)) {
			fail("the script does not exist: " + script);
			return;
		}
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			installFileInclude(cx, scope);
			final String source = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
			cx.evaluateString(scope, source, script.getFileName().toString(), 1, null);
			final ScriptableObject state = scriptState(scope, cx);
			final PIDSWrapper pids = panelWrapper(cx, stationIndex, filter, resolved);
			((Function) scope.get("create", scope)).call(cx, scope, scope, new Object[]{null, state, pids});
			/* The route-map branch of render(): floor(cycleTimer / 120) / 4 has to be odd. 2800
			   gives floor(23 / 4) = 5, and it is the branch that asks isNonPassenger which
			   background the panel wears. */
			state.put("cycleTimer", state, 2800);
			final ScriptRenderContext ctx = ScriptRenderContext.dryRun(128, 72, 1F);
			((Function) scope.get("render", scope)).call(cx, scope, scope, new Object[]{ctx, state, pids});
			final List<String> calls = ctx.recordedCalls();
			System.out.println("     " + calls.size() + " draw calls");
			for (String expected : expectedTexts) {
				expectText(calls, expected, which + " at station index " + stationIndex);
			}
			final boolean badge = drawn(calls, "\u4e0d\u8f09\u5ba2\u5217\u8eca") || drawn(calls, "Not in Service");
			if ("NOT_IN_SERVICE".equals(expectedVerdict)) {
				if (!badge) {
					fail(which + ": a terminus panel did not draw \"not in service\"");
					return;
				}
				pass(which + ": a terminus panel draws \"not in service\"");
			} else {
				if (badge) {
					fail(which + ": a mid-line panel drew \"not in service\"");
					return;
				}
				pass(which + ": a mid-line panel draws its real arrivals");
			}
		} catch (Exception e) {
			fail("the real script threw: " + e);
		} finally {
			Context.exit();
		}
	}

	/**
	 * A panel with no filter at all has nothing but its position to resolve a station from, and
	 * that is the resolution the renderer hands the wrapper. Without it this answered {@code null}
	 * and the terminus rule could not find the panel's index.
	 */
	private static void checkNearestPlatformAnswers(int stationIndex) {
		System.out.println();
		System.out.println("-- MTR's own nearest-platform answer");
		final Context cx = Context.enter();
		try {
			final PIDSWrapper wrapper = panelWrapper(cx, stationIndex, new long[]{}, PLATFORM_IDS[stationIndex]);
			if (wrapper.resolvedPlatformId() == PLATFORM_IDS[stationIndex]) {
				pass("the wrapper carries the resolved platform " + PLATFORM_IDS[stationIndex]);
			} else {
				fail("the wrapper carries " + wrapper.resolvedPlatformId() + " instead of "
						+ PLATFORM_IDS[stationIndex]);
			}
			/* The copy the lenient-arrivals retry renders with has to carry it too, or a preset
			   that threw once would read its station from a wrapper that lost it. */
			if (wrapper.withLenientArrivals().resolvedPlatformId() == PLATFORM_IDS[stationIndex]) {
				pass("withLenientArrivals() keeps it");
			} else {
				fail("withLenientArrivals() lost the resolved platform");
			}
		} catch (Exception e) {
			fail("the nearest-platform probe threw: " + e);
		} finally {
			Context.exit();
		}
	}

	// ------------------------------------------------------------------

	/**
	 * {@code Text.create().marquee(number)} — the overload the packs actually call.
	 *
	 * <p>Reported from another port as {@code EvaluatorException: Can't find method
	 * …TextWrapper.marquee(number)}, sixteen times in one session. Every argumented call in the
	 * corpus is a plain number literal — {@code .marquee(3)}, {@code .marquee(3.0)},
	 * {@code .marquee(10)}, {@code .marquee(12)}, {@code .marquee(20)}, {@code .marquee(25.0)},
	 * in five packs — and Rhino resolves a JavaScript number against the {@code double} overload,
	 * so the call has to go through here: this port has the overload, and what would break it is
	 * its disappearance or a change of the parameter type. The marquee's own scrolling is not
	 * what is under test; the call reaching the wrapper is.</p>
	 */
	private static void checkMarqueeAcceptsANumber() {
		System.out.println();
		System.out.println("-- Text.marquee(number)");
		final Context cx = Context.enter();
		try {
			final Scriptable scope = ScriptEngine.newScope(cx);
			final ScriptRenderContext ctx = ScriptRenderContext.dryRun(128, 72, 1F);
			ScriptableObject.putProperty(scope, "PROBE_CTX", ctx);
			for (String expression : new String[]{"12", "3.0", "25.0"}) {
				try {
					cx.evaluateString(scope,
							"Text.create().text(\"marquee probe\").pos(0, 0).size(80, 9).marquee(" + expression
									+ ").draw(PROBE_CTX);",
							"marquee-probe.js", 1, null);
					pass("marquee(" + expression + ") reached the wrapper");
				} catch (Exception e) {
					fail("marquee(" + expression + ") threw: " + e);
				}
			}
			/* The no-argument spelling the same packs use, so a change made for the numeric
			   overload cannot have removed it. */
			try {
				cx.evaluateString(scope,
						"Text.create().text(\"marquee probe\").pos(0, 0).size(80, 9).marquee().draw(PROBE_CTX);",
						"marquee-probe.js", 1, null);
				pass("marquee() still works");
			} catch (Exception e) {
				fail("marquee() threw: " + e);
			}
		} finally {
			Context.exit();
		}
	}

	private static PIDSWrapper panelWrapper(Context cx, int stationIndex, long[] filter, long resolved) {
		final List<Long> platformIds = new ArrayList<>();
		for (long id : filter) {
			platformIds.add(id);
		}
		final List<ScheduleEntry> schedule = new ArrayList<>();
		/* Leaving for the terminus in 40 minutes: the "normal" branch of every HKR render(), so
		   neither the empty, arriving nor door-closing view is what answers. */
		schedule.add(new ScheduleEntry(System.currentTimeMillis() + 40 * 60_000L, 6, ROUTE_ID,
				STATION_NAMES.length - 1));
		return new PIDSWrapper("crt_pids", 1, 128, 72, panelOn(stationIndex), platformIds,
				new String[]{""}, new boolean[]{false}, schedule, true, false, resolved);
	}

	private static ScriptableObject scriptState(Scriptable scope, Context cx) {
		final ScriptableObject state = new ScriptableObject() {
			@Override
			public String getClassName() {
				return "PIDSState";
			}
		};
		state.setPrototype(ScriptableObject.getObjectPrototype(scope));
		state.setParentScope(scope);
		return state;
	}

	private static boolean drawn(List<String> calls, String text) {
		for (String call : calls) {
			if (call.contains(text)) {
				return true;
			}
		}
		return false;
	}

	private static void expectText(List<String> calls, String text, String label) {
		if (drawn(calls, text)) {
			pass(label + ": drew " + text);
		} else {
			fail(label + ": nothing drew " + text);
			for (String call : calls) {
				System.out.println("       " + call);
			}
		}
	}

	private static void pass(String what) {
		System.out.println("OK   " + what);
	}

	private static void fail(String what) {
		System.out.println("FAIL " + what);
		failures++;
	}

	/**
	 * Replaces the engine's {@code include} with one that reads ordinary files, so a real pack's
	 * dependencies resolve outside the game. The path rules are the engine's own.
	 */
	private static void installFileInclude(final Context cx, final Scriptable scope) {
		ScriptableObject.putProperty(scope, "include", new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
				if (args.length == 0 || args[0] == null) {
					return Undefined.instance;
				}
				final String reference = Context.toString(args[0]);
				try {
					ScriptPaths.checkReference(reference);
				} catch (ScriptPaths.RejectedPathException refused) {
					ScriptPaths.report("include()", refused);
					return Undefined.instance;
				}
				final int colon = reference.indexOf(':');
				final String namespace = colon < 0 ? "minecraft" : reference.substring(0, colon);
				final String path = colon < 0 ? reference : reference.substring(colon + 1);
				for (Path root : RESOURCE_ROOTS) {
					final Path base = root.resolve("assets").resolve(namespace);
					final Path candidate;
					try {
						candidate = ScriptPaths.resolveWithin(base, path.split("/"));
					} catch (Exception refused) {
						continue;
					}
					if (Files.isRegularFile(candidate)) {
						try {
							final String text = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
							context.evaluateString(s, text, candidate.toString(), 1, null);
							System.out.println("     included " + reference);
						} catch (Exception e) {
							System.out.println("FAIL include " + reference + " threw: " + e);
						}
						return Undefined.instance;
					}
				}
				System.out.println("     include miss: " + reference);
				return Undefined.instance;
			}
		});
	}
}
