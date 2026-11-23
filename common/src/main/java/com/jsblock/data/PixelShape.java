package com.jsblock.data;

import java.util.Locale;

/**
 * What one pixel of a pixelated PIDS panel is drawn as.
 *
 * <p>Whole-screen pixelation renders a panel small and magnifies it, so its text and icons land on
 * one coarse grid. What that grid looks like is a separate question, and the two answers are the two
 * kinds of screen these packs imitate: a blocky LCD, whose pixels are squares and touch each other,
 * and a dot-matrix board, whose pixels are round and separated by a dark gap.</p>
 *
 * <p>A resource pack declares it per preset ({@code "pixelShape": "circle"}) or for all of its
 * presets at once (the {@code pixelation} block read by {@link PackPixelation}); a player overrides
 * it per preset in the client config. Absent everywhere means {@link #SQUARE}, which is what version
 * 1.4 did and therefore what nobody has to ask for.</p>
 */
public enum PixelShape {

    /** Square pixels, edge to edge. The original behaviour. */
    SQUARE,

    /** Round dots with a gap between them, the way a dot-matrix display is built. */
    CIRCLE;

    /** The name used in configuration files. */
    public String configName() {
        return this == CIRCLE ? "circle" : "square";
    }

    /**
     * @param name what a pack or a player wrote; case and surrounding spaces do not matter.
     * @return the shape that name means, or {@code null} when it means nothing -- the caller decides
     *         whether to warn and carry on with the default or to refuse the entry.
     */
    public static PixelShape byName(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "square":
            case "squares":
            case "block":
            case "blocks":
            case "lcd":
            case "方块":
            case "像素方块":
                return SQUARE;
            case "circle":
            case "circles":
            case "round":
            case "dot":
            case "dots":
            case "dotmatrix":
            case "dot_matrix":
            case "点阵":
            case "圆形":
            case "点阵圆形":
                return CIRCLE;
            default:
                return null;
        }
    }
}
