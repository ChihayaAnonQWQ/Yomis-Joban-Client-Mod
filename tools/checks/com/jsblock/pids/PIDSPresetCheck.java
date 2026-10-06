package com.jsblock.pids;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsblock.data.PIDSPreset;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Standalone check that the presets YJCM ships actually parse into component layouts.
 *
 * <p>Compiling proves nothing about whether {@link PIDSLayout} understands the JSON in
 * {@code assets/jsblock/joban_custom_resources.json}, so this runs the real
 * {@link PIDSPreset#fromJson} over every shipped preset and reports the component types it
 * produced. It also feeds one deliberately malformed preset through to confirm a single bad
 * element is skipped instead of taking the whole preset down.</p>
 *
 * <p>Run via the {@code pidsPresetCheck} task (see {@code tools/init-pids-check.gradle}).</p>
 */
public final class PIDSPresetCheck {

	private static final String DEFAULT_RESOURCE = "assets/jsblock/joban_custom_resources.json";

	private PIDSPresetCheck() {
	}

	public static void main(String[] args) throws Exception {
		int failures = 0;

		failures += checkShippedPresets(args.length > 0 ? args[0] : DEFAULT_RESOURCE);
		failures += checkMalformedPresetIsSurvivable();

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: ALL CHECKS PASSED" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
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
