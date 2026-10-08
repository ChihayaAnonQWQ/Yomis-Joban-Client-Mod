package com.jsblock.data;

import com.google.gson.JsonObject;
import com.jsblock.Joban;

/**
 * The {@code pixelation} block a resource pack may put at the top level of its
 * {@code joban_custom_resources.json}:
 *
 * <pre>
 * {
 *   "pixelation": { "enabled": true, "scale": 3, "shape": "circle" },
 *   "pids_images": [ ... ]
 * }
 * </pre>
 *
 * <p>Version 1.4 let a pack declare pixelation per preset and nothing else, which means a pack that
 * wants the whole board drawn on one grid has to repeat itself on every preset, and a pack that does
 * <em>not</em> want it has no way to say so at all -- a preset that carries a {@code pixelScale} is
 * pixelated whether the pack likes it or not.</p>
 *
 * <p>This is the pack-level half of that: {@code enabled} is the pack saying whether it wants any of
 * its presets pixelated, {@code scale} is the resolution it wants by default, {@code shape} is which
 * of the two kinds of screen it is imitating. A preset's own {@code pixelScale} / {@code pixelShape}
 * still win over these, and a player's config wins over both -- the same order the scale already
 * followed, so nothing that worked before changes meaning.</p>
 *
 * <p>Several packs can each carry a block; the last one read wins, since there is one world and one
 * set of panels in it. A pack that needs to be exact should declare per preset.</p>
 */
public final class PackPixelation {

    /** Nothing declared: behave exactly as 1.4 did, i.e. only a preset's own scale enables it. */
    private static boolean enabled = true;
    private static int scale = 1;
    private static PixelShape shape = PixelShape.SQUARE;
    /** Dots across and down, or {@code null} when the pack asked for a divisor instead. */
    private static int[] resolution = null;
    /** The lamp grid: how many dots the board has, independent of how finely it is drawn. */
    private static int[] dots = null;

    private PackPixelation() {
    }

    /** Back to "no pack has said anything". Called when the resource packs are reloaded. */
    public static void reset() {
        enabled = true;
        scale = 1;
        shape = PixelShape.SQUARE;
        resolution = null;
        dots = null;
    }

    /** Reads the block, if this pack has one. Anything unreadable is ignored with one log line. */
    public static void read(JsonObject packJson) {
        if (packJson == null || !packJson.has("pixelation") || !packJson.get("pixelation").isJsonObject()) {
            return;
        }
        final JsonObject block = packJson.getAsJsonObject("pixelation");

        if (block.has("enabled")) {
            try {
                enabled = block.get("enabled").getAsBoolean();
            } catch (Exception e) {
                Joban.LOGGER.warn("[Joban Client] Ignoring pixelation.enabled: not a boolean.");
            }
        }

        if (block.has("scale")) {
            try {
                final int declared = block.get("scale").getAsInt();
                if (declared < 1) {
                    Joban.LOGGER.warn("[Joban Client] Ignoring pixelation.scale " + declared + ": below 1.");
                } else {
                    scale = declared;
                }
            } catch (Exception e) {
                Joban.LOGGER.warn("[Joban Client] Ignoring pixelation.scale: not a whole number.");
            }
        }

        if (block.has("shape")) {
            try {
                final String declared = block.get("shape").getAsString();
                final PixelShape parsed = PixelShape.byName(declared);
                if (parsed == null) {
                    Joban.LOGGER.warn("[Joban Client] Ignoring pixelation.shape \"" + declared
                            + "\": expected \"square\" or \"circle\".");
                } else {
                    shape = parsed;
                }
            } catch (Exception e) {
                Joban.LOGGER.warn("[Joban Client] Ignoring pixelation.shape: not a string.");
            }
        }

        if (block.has("resolution")) {
            resolution = parseResolution(block.get("resolution"), "pixelation.resolution");
        }

        /* The lamp grid, when the board has one that is coarser than its picture. Left unset it
           equals the target, which is the "one dot per rendered pixel" case this feature started
           as. */
        if (block.has("dots")) {
            dots = parseResolution(block.get("dots"), "pixelation.dots");
        }

        Joban.LOGGER.info("[Joban Client] Resource pack declares pixelation: enabled=" + enabled
                + " scale=" + scale + " shape=" + shape.configName()
                + (resolution == null ? "" : " resolution=" + resolution[0] + "x" + resolution[1]));
    }

    /** @return whether the pack wants its presets pixelated at all. */
    public static boolean enabled() {
        return enabled;
    }

    /** @return the scale the pack wants by default; 1 when it declared none. */
    public static int scale() {
        return scale;
    }

    /** @return the shape the pack wants by default. */
    public static PixelShape shape() {
        return shape;
    }

    /** @return the grid the pack asked for in dots, or {@code null} when it asked for a divisor. */
    public static int[] resolution() {
        return resolution;
    }

    /** @return the lamp grid the pack asked for, or {@code null} when it wants one dot per pixel. */
    public static int[] dots() {
        return dots;
    }

    /** The pack-side lamp grid for one preset, before the player's config is consulted. */
    public static int[] packDotsFor(int[] presetDots) {
        if (!enabled) {
            return null;
        }
        return presetDots != null ? presetDots : dots;
    }

    /**
     * Reads a dot resolution, accepting the three shapes a pack author might reasonably write:
     *
     * <pre>
     *   "pixelResolution": 96                     // dots across; the height follows from the canvas
     *   "pixelResolution": [96, 54]               // dots across and down
     *   "pixelResolution": { "width": 96, "height": 54 }
     * </pre>
     *
     * @return {@code {width, height}} with a height of 0 meaning "work it out from the canvas", or
     *         {@code null} when there is nothing readable to use
     */
    public static int[] parseResolution(com.google.gson.JsonElement element, String what) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            if (element.isJsonPrimitive()) {
                final int width = element.getAsInt();
                return width >= 1 ? new int[]{width, 0} : warnResolution(what);
            }
            if (element.isJsonArray()) {
                final com.google.gson.JsonArray array = element.getAsJsonArray();
                if (array.size() == 0) {
                    return warnResolution(what);
                }
                final int width = array.get(0).getAsInt();
                final int height = array.size() >= 2 ? array.get(1).getAsInt() : 0;
                return width >= 1 ? new int[]{width, Math.max(0, height)} : warnResolution(what);
            }
            if (element.isJsonObject()) {
                final com.google.gson.JsonObject object = element.getAsJsonObject();
                if (!object.has("width")) {
                    return warnResolution(what);
                }
                final int width = object.get("width").getAsInt();
                final int height = object.has("height") ? object.get("height").getAsInt() : 0;
                return width >= 1 ? new int[]{width, Math.max(0, height)} : warnResolution(what);
            }
        } catch (Exception e) {
            Joban.LOGGER.warn("[Joban Client] Ignoring " + what + ": not a number, an array or an object.");
            return null;
        }
        return warnResolution(what);
    }

    private static int[] warnResolution(String what) {
        Joban.LOGGER.warn("[Joban Client] Ignoring " + what + ": the width has to be at least 1 dot.");
        return null;
    }

    /**
     * The offscreen target's size in pixels: what a preset's scale or a pack's resolution asks for,
     * corrected so its proportions are the canvas's.
     *
     * <p>It lives here rather than in the renderer so the headless check can call it: this is pure
     * arithmetic over four numbers, and the aspect rule is the part of the feature most likely to be
     * quietly wrong.</p>
     *
     * <p>The composite quad stretches the target across the whole panel, so a target whose aspect
     * ratio differs from the canvas's comes out squashed -- and a pack asking for 96x54 dots on a
     * 136x76 board is asking for exactly that, by about one percent. The width is taken as the
     * intent and the height recomputed from it and the canvas, and the correction is reported once
     * so a pack author sees that their numbers were read as a width rather than quietly getting a
     * different board.</p>
     *
     * @param canvasWidth  the script canvas width in script units
     * @param canvasHeight the script canvas height
     * @param pixelScale   the divisor to use when no resolution applies; 1 or less means "none"
     * @param resolution   {@code {width, height}} in dots, height 0 meaning "work it out", or null
     * @return {@code {width, height}} in pixels, or {@code null} when neither applies
     */
    public static int[] targetSize(int canvasWidth, int canvasHeight, int pixelScale, int[] resolution) {
        if (resolution != null && resolution.length >= 1 && resolution[0] >= 1) {
            /* A resolution above the canvas is a request to render finer than the script's own units
               and let the composite shrink it, which is how a panel gets smooth text rather than
               blocky text. It is not clamped to the canvas for that reason, only to a ceiling that
               keeps a typo from asking for a gigabyte of framebuffer. */
            final int width = Math.min(resolution[0], MAX_DOTS);
            final int fromCanvas = Math.max(1, Math.round(width * (float) canvasHeight / canvasWidth));
            int height = resolution.length >= 2 && resolution[1] >= 1
                    ? Math.min(resolution[1], MAX_DOTS)
                    : fromCanvas;
            if (height != fromCanvas) {
                reportResolutionCorrection(canvasWidth, canvasHeight, width, height, fromCanvas);
                height = fromCanvas;
            }
            return new int[]{width, height};
        }
        if (pixelScale <= 1) {
            return null;
        }
        return new int[]{Math.max(1, canvasWidth / pixelScale), Math.max(1, canvasHeight / pixelScale)};
    }

    /**
     * The largest grid accepted per axis.
     *
     * <p>4096 dots is about 30x the largest PIDS canvas, which is far past any use: past a certain
     * point the panel on screen has fewer pixels than the target has dots, and the extra ones cost
     * a framebuffer each without being visible. The ceiling exists so a mistyped resolution cannot
     * ask for one.</p>
     */
    public static final int MAX_DOTS = 4096;

    /** Grids whose declared height had to be recomputed, so the log says it once each. */
    private static final java.util.Set<String> resolutionCorrections = new java.util.HashSet<>();

    private static void reportResolutionCorrection(int canvasWidth, int canvasHeight, int width,
                                                   int requestedHeight, int corrected) {
        if (!resolutionCorrections.add(canvasWidth + "x" + canvasHeight + "@" + width + "x" + requestedHeight)) {
            return;
        }
        Joban.LOGGER.warn("[PIDS pixelation] A resolution of " + width + "x" + requestedHeight
                + " does not keep the canvas's " + canvasWidth + "x" + canvasHeight + " proportions;"
                + " using " + width + "x" + corrected + " so the panel is not stretched. Reported once.");
    }

    /**
     * The pack-side grid for one preset, before the player's config is consulted.
     *
     * @param presetResolution the preset's own declaration, or {@code null}
     */
    public static int[] packResolutionFor(int[] presetResolution) {
        if (!enabled) {
            return null;
        }
        return presetResolution != null ? presetResolution : resolution;
    }

    /**
     * The pack-side scale for one preset, before the player's config is consulted.
     *
     * @param presetScale the preset's own {@code pixelScale}; 1 when it declared none
     * @return the preset's declaration if it made one, otherwise the pack default, otherwise 1 --
     *         and 1 for everything when the pack switched pixelation off
     */
    public static int packScaleFor(int presetScale) {
        if (!enabled) {
            return 1;
        }
        return presetScale > 1 ? presetScale : scale;
    }

    /**
     * The pack-side shape for one preset.
     *
     * @param presetShape the preset's own {@code pixelShape}, or {@code null} when it declared none
     */
    public static PixelShape packShapeFor(PixelShape presetShape) {
        return presetShape != null ? presetShape : shape;
    }
}
