package com.jsblock.pids;

import com.google.gson.JsonParser;
import com.jsblock.data.PackPixelation;
import com.jsblock.data.PixelShape;

/**
 * Standalone check for the pixelation sizing rules.
 *
 * <p>The rule that matters is the one a pack can get wrong without noticing: the offscreen target is
 * stretched across the whole panel, so a grid whose proportions differ from the canvas's comes out
 * squashed. A pack asking for 96x54 dots on a 136x76 board is asking for a 1% squash, which is
 * invisible in a config file and visible on a board, so the height is recomputed from the width and
 * the canvas and that correction is asserted here rather than trusted.</p>
 *
 * <p>Also covers the three shapes a resolution can be written in and the scale fallback, because a
 * malformed one has to mean "no pixelation", not "divide by zero".</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class PixelationCheck {

	private static int failures = 0;

	private PixelationCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== shape names ==");
		check("square -> SQUARE", PixelShape.byName("square") == PixelShape.SQUARE);
		check("SQUARE (case) -> SQUARE", PixelShape.byName(" SQUARE ") == PixelShape.SQUARE);
		check("circle -> CIRCLE", PixelShape.byName("circle") == PixelShape.CIRCLE);
		check("dots -> CIRCLE", PixelShape.byName("dots") == PixelShape.CIRCLE);
		check("点阵圆形 -> CIRCLE", PixelShape.byName("点阵圆形") == PixelShape.CIRCLE);
		check("gibberish -> null", PixelShape.byName("zigzag") == null);
		check("null -> null", PixelShape.byName(null) == null);

		System.out.println();
		System.out.println("== resolution parsing ==");
		checkParsed("96", 96, 0);
		checkParsed("[96,54]", 96, 54);
		checkParsed("[96]", 96, 0);
		checkParsed("{\"width\":96,\"height\":54}", 96, 54);
		checkParsed("{\"width\":96}", 96, 0);
		check("[] -> null", PackPixelation.parseResolution(new JsonParser().parse("[]"), "check") == null);
		check("{\"height\":54} -> null",
				PackPixelation.parseResolution(new JsonParser().parse("{\"height\":54}"), "check") == null);
		check("0 -> null", PackPixelation.parseResolution(new JsonParser().parse("0"), "check") == null);
		check("\"abc\" -> null (no throw)",
				PackPixelation.parseResolution(new JsonParser().parse("\"abc\""), "check") == null);

		System.out.println();
		System.out.println("== target sizes ==");
		checkSize("scale 4, no resolution, 136x76", 136, 76, 4, null, 34, 19);
		checkSize("scale 1, no resolution", 136, 76, 1, null, 0, 0);
		checkSize("resolution 34x19, 136x76", 136, 76, 1, new int[]{34, 19}, 34, 19);
		checkSize("resolution wins over the scale", 136, 76, 6, new int[]{34, 19}, 34, 19);
		checkSize("width only, 136x76", 136, 76, 1, new int[]{96, 0}, 96, 54);
		checkSize("width only, 186x60 (the 1A canvas)", 186, 60, 1, new int[]{96, 0}, 96, 31);
		checkSize("height corrected from the width", 136, 76, 1, new int[]{34, 20}, 34, 19);
		checkSize("height corrected again, 1A", 186, 60, 1, new int[]{62, 40}, 62, 20);
		checkSize("ten times finer than the canvas", 136, 76, 1, new int[]{1360, 760}, 1360, 760);
		checkSize("ten times finer, 1A", 186, 60, 1, new int[]{1860, 600}, 1860, 600);
		checkSize("above the ceiling", 136, 76, 1, new int[]{99999, 0}, 4096, 2289);
		/* An explicit height loses to the proportions, by design: a pack that writes 1360x700 for a
		   136x76 canvas is describing a squash it does not want, and the width is the intent. */
		checkSize("a deliberately squashed height is corrected", 136, 76, 1, new int[]{1360, 700}, 1360, 760);
		checkSize("both axes beyond the ceiling", 136, 76, 1, new int[]{99999, 99999}, 4096, 2289);
		checkSize("one dot", 136, 76, 1, new int[]{1, 1}, 1, 1);

		System.out.println();
		System.out.println("== proportions always match the canvas ==");
		final int[][] canvases = {{136, 76}, {186, 60}, {133, 72}};
		final int[] widths = {8, 17, 34, 48, 96, 128};
		for (final int[] canvas : canvases) {
			for (final int width : widths) {
				final int[] size = PackPixelation.targetSize(canvas[0], canvas[1], 1, new int[]{width, 0});
				if (size == null) {
					fail("resolution " + width + " on " + canvas[0] + "x" + canvas[1] + " returned null");
					continue;
				}
				final double wanted = (double) canvas[0] / canvas[1];
				final double got = (double) size[0] / size[1];
				/* One pixel of rounding moves the ratio by up to half a dot at these sizes; a
				   squash worth correcting is orders of magnitude larger than that. */
				final double slack = 0.5 / size[1] + 0.01;
				final boolean ok = Math.abs(wanted - got) <= slack * wanted;
				check("canvas " + canvas[0] + "x" + canvas[1] + " grid " + width + " -> "
						+ size[0] + "x" + size[1] + " (ratio " + String.format("%.3f", got)
						+ " vs " + String.format("%.3f", wanted) + ")", ok);
			}
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: PIXELATION OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void checkParsed(String json, int width, int height) {
		final int[] parsed = PackPixelation.parseResolution(new JsonParser().parse(json), "check");
		final boolean ok = parsed != null && parsed[0] == width && parsed[1] == height;
		check(json + " -> " + width + "x" + height, ok);
	}

	private static void checkSize(String what, int canvasWidth, int canvasHeight, int scale,
								  int[] resolution, int width, int height) {
		final int[] size = PackPixelation.targetSize(canvasWidth, canvasHeight, scale, resolution);
		if (width == 0 && height == 0) {
			check(what + " -> none", size == null);
			return;
		}
		final boolean ok = size != null && size[0] == width && size[1] == height;
		check(what + " -> " + width + "x" + height + (ok ? "" : " (got "
				+ (size == null ? "null" : size[0] + "x" + size[1]) + ")"), ok);
	}

	private static void check(String what, boolean ok) {
		if (ok) {
			System.out.println("OK   " + what);
		} else {
			fail(what);
		}
	}

	private static void fail(String what) {
		System.out.println("FAIL " + what);
		failures++;
	}
}
