package com.jsblock.pids;

/**
 * Describes where a PIDS panel sits and how big it is, in the block-entity render space.
 *
 * <p>Each concrete PIDS renderer ({@code RenderLCDPIDS}, {@code RenderRVPIDS}) receives this
 * geometry through its constructor, because the panel size differs per block. Handing it to
 * {@link PIDSLayout} lets a component layout be drawn onto exactly the rectangle the
 * built-in renderer uses for its background artwork.</p>
 *
 * <h2>Panel origin</h2>
 * <p>The origin is <b>not</b> derivable from {@link #panelWidth}: the built-in renderers draw
 * their background at {@code startX - 21F / 2} (LCD) and {@code startX - 26F / 2} (RV), and
 * neither constant equals half the background width. So the offsets are carried explicitly
 * here, copied verbatim from those two draw calls.</p>
 *
 * <table>
 *   <caption>Offsets derived from the built-in renderers</caption>
 *   <tr><th></th><th>{@code panelOffsetX}</th><th>{@code panelOffsetY}</th><th>size</th></tr>
 *   <tr><td>LCD PIDS</td><td>{@code -21F / 2F = -10.5F}</td><td>{@code -1F - 1.5F = -2.5F}</td><td>111 x 60</td></tr>
 *   <tr><td>RV PIDS</td><td>{@code -26F / 2F = -13F}</td><td>{@code BACKGROUND_Y - 1.5F = -11F}</td><td>119 x 65.8</td></tr>
 * </table>
 *
 * <p>The vertical parts come from the extra {@code matrices.translate(0, y, 0.01)} each
 * renderer applies before drawing the background quad at {@code -1.5F}.</p>
 */
public class PIDSGeometry {

	/** Panel centre X in the renderer's local space, as passed to the renderer constructor. */
	public final float startX;
	/** Panel top Y in the renderer's local space. */
	public final float startY;
	/** Panel Z in the renderer's local space. */
	public final float startZ;
	/** Uniform scale divisor applied to the model-view stack. */
	public final float scale;
	/** Background artwork width in local units. */
	public final float panelWidth;
	/** Background artwork height in local units. */
	public final float panelHeight;
	/**
	 * X of the background artwork's left edge relative to {@link #startX}, copied from the
	 * renderer's own {@code startX - N/2} constant. Negative in both renderers.
	 */
	public final float panelOffsetX;
	/**
	 * Y of the background artwork's top edge in the renderer's local space, i.e. the sum of
	 * the renderer's own {@code BACKGROUND_Y} translate and the shared {@code -1.5F} the
	 * background quad is drawn at.
	 */
	public final float panelOffsetY;
	/** Whether the panel is rotated a further 90 degrees. */
	public final boolean rotate90;
	/** Extra X rotation applied to the panel. */
	public final float rotation;
	/** Font resource location used when a preset does not specify one. */
	public final String defaultFont;
	/** Text colour used when a preset does not specify one. */
	public final int defaultTextColor;

	public PIDSGeometry(float startX, float startY, float startZ, float scale,
						float panelWidth, float panelHeight,
						float panelOffsetX, float panelOffsetY,
						boolean rotate90, float rotation,
						String defaultFont, int defaultTextColor) {
		this.startX = startX;
		this.startY = startY;
		this.startZ = startZ;
		this.scale = scale;
		this.panelWidth = panelWidth;
		this.panelHeight = panelHeight;
		this.panelOffsetX = panelOffsetX;
		this.panelOffsetY = panelOffsetY;
		this.rotate90 = rotate90;
		this.rotation = rotation;
		this.defaultFont = defaultFont;
		this.defaultTextColor = defaultTextColor;
	}

	/**
	 * @return the panel's left edge in the renderer's local space, matching where the
	 * built-in renderer puts its background quad.
	 */
	public float panelLeft() {
		return startX + panelOffsetX;
	}
}
