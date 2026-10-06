package com.jsblock.pids;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsblock.data.PIDSPreset;
import mtr.data.ScheduleEntry;
import net.minecraft.core.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standalone check that the presets YJCM ships actually parse into component layouts, and
 * that the row semantics layouts rely on match the built-in renderers.
 *
 * <p>Compiling proves nothing about whether {@link PIDSLayout} understands the JSON in
 * {@code assets/jsblock/joban_custom_resources.json}, so this runs the real
 * {@link PIDSPreset#fromJson} over every shipped preset and reports the component types it
 * produced. It also feeds one deliberately malformed preset through to confirm a single bad
 * element is skipped instead of taking the whole preset down.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class PIDSPresetCheck {

	private static final String DEFAULT_RESOURCE = "assets/jsblock/joban_custom_resources.json";

	private PIDSPresetCheck() {
	}

	public static void main(String[] args) throws Exception {
		int failures = 0;

		failures += checkShippedPresets(args.length > 0 ? args[0] : DEFAULT_RESOURCE);
		failures += checkMalformedPresetIsSurvivable();
		failures += checkRowMapping();

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: ALL CHECKS PASSED" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	/**
	 * Verifies that a component's {@code row} indexes <em>display</em> rows using the same
	 * consumption rule as {@code RenderLCDPIDS} / {@code RenderRVPIDS}: a hidden row does
	 * not consume an arrival, so the arrival surfaces in the next visible row.
	 *
	 * <p>The expected values below are hand-derived from the renderer loop: it iterates
	 * {@code i} over the display rows, draws {@code schedule[entryIndex]}, and only runs
	 * {@code entryIndex++} when the row was not skipped by {@code hideArrivals[i]}.</p>
	 */
	private static int checkRowMapping() {
		System.out.println();
		System.out.println("== display-row mapping ==");
		int failures = 0;

		// Five arrivals; arrivalMillis 1000..5000 so the "shown arrival" is its 1-based index.
		final List<ScheduleEntry> schedule = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			schedule.add(new ScheduleEntry(1000L * (i + 1), 4, 100L + i, i));
		}

		failures += expectRows("no hidden rows",
				new boolean[]{false, false, false, false}, schedule,
				new long[]{1, 2, 3, 4});

		// Row 1 hidden -> consumes nothing, so row 2 shows schedule[1] and row 3 shows
		// schedule[2]; schedule[3] and schedule[4] never reach a display row.
		failures += expectRows("row 1 hidden",
				new boolean[]{false, true, false, false}, schedule,
				new long[]{1, -1, 2, 3});

		failures += expectRows("row 0 hidden",
				new boolean[]{true, false, false, false}, schedule,
				new long[]{-1, 1, 2, 3});

		failures += expectRows("more rows than arrivals",
				new boolean[]{false, false, false, false}, schedule.subList(0, 2),
				new long[]{1, 2, -1, -1});

		return failures;
	}

	/**
	 * @param expected arrival number per display row, where {@code -1} means "no arrival".
	 *                 Numbering starts at 1 so 0 stays unambiguous.
	 */
	private static int expectRows(String label, boolean[] hidden, List<ScheduleEntry> schedule, long[] expected) {
		final PIDSContext context = new PIDSContext(
				null, new BlockPos(0, 0, 0), null, new String[hidden.length],
				new ArrayList<>(schedule), Collections.emptyList(), hidden, 0D, 0L);

		final StringBuilder actual = new StringBuilder();
		boolean ok = true;
		for (int row = 0; row < expected.length; row++) {
			final ScheduleEntry entry = context.arrival(row);
			final long shown = entry == null ? -1 : entry.arrivalMillis / 1000L;
			if (row > 0) {
				actual.append(", ");
			}
			actual.append(shown);
			if (shown != expected[row]) {
				ok = false;
			}
		}

		if (ok) {
			System.out.println("OK   " + label + " -> rows [" + actual + "]");
			return 0;
		}
		System.out.println("FAIL " + label
				+ "\n       expected [" + join(expected) + "]"
				+ "\n       actual   [" + actual + "]");
		return 1;
	}

	private static String join(long[] values) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(values[i]);
		}
		return builder.toString();
	}

	private static int checkShippedPresets(String resourcePath) throws Exception {
		System.out.println("== shipped presets: " + resourcePath + " ==");
		int failures = 0;
		try (InputStream stream = PIDSPresetCheck.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (stream == null) {
				System.out.println("FAIL resource not found on classpath: " + resourcePath);
				return 1;
			}
			final JsonObject root = new JsonParser()
					.parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
			final JsonArray presets = root.getAsJsonArray("pids_images");
			System.out.println("     presets declared: " + presets.size());

			for (JsonElement element : presets) {
				final JsonObject json = element.getAsJsonObject();
				final String id = json.get("id").getAsString();
				try {
					final PIDSPreset preset = PIDSPreset.fromJson(json);
					if (preset.layout == null) {
						System.out.println("FAIL " + id + ": layout == null, \"components\" was not parsed");
						failures++;
						continue;
					}
					if (preset.layout.isEmpty()) {
						System.out.println("FAIL " + id + ": layout parsed but holds no components");
						failures++;
						continue;
					}
					System.out.println("OK   " + id
							+ "\n       canvas     = " + preset.layout.getDesignWidth() + " x " + preset.layout.getDesignHeight()
							+ "\n       background = " + preset.image
							+ "\n       font       = " + preset.font
							+ "\n       components = " + preset.layout.describeComponents());
				} catch (Exception e) {
					System.out.println("FAIL " + id + ": " + e);
					failures++;
				}
			}
		}
		return failures;
	}

	/**
	 * A preset whose second element names a component this branch does not implement must
	 * still yield a usable layout containing the other components.
	 */
	private static int checkMalformedPresetIsSurvivable() {
		System.out.println();
		System.out.println("== resilience: one unknown component among valid ones ==");
		final String json = "{"
				+ "\"id\":\"__check_unknown_component\","
				+ "\"size\":[10,10],"
				+ "\"components\":["
				+ "  {\"component\":\"clock\",\"x\":0,\"y\":0,\"width\":5,\"height\":5},"
				+ "  {\"component\":\"this_component_does_not_exist\",\"x\":0,\"y\":0,\"width\":5,\"height\":5},"
				+ "  {\"component\":\"custom_text\",\"x\":0,\"y\":5,\"width\":5,\"height\":5,\"text\":\"ok\"}"
				+ "]}";
		try {
			final PIDSPreset preset = PIDSPreset.fromJson(new JsonParser().parse(json));
			if (preset.layout == null) {
				System.out.println("FAIL unknown component took the whole layout down (layout == null)");
				return 1;
			}
			final int size = preset.layout.getComponents().size();
			System.out.println("     components kept: " + size + " -> " + preset.layout.describeComponents());
			if (size != 2) {
				System.out.println("FAIL expected the 2 valid components to survive, kept " + size);
				return 1;
			}
			System.out.println("OK   unknown component skipped, valid ones preserved");
			return 0;
		} catch (Exception e) {
			System.out.println("FAIL threw instead of degrading: " + e);
			return 1;
		}
	}
}
