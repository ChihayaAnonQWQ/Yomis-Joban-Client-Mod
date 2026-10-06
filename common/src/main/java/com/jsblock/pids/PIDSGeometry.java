package com.jsblock.pids;

/**
 * Describes where a PIDS panel sits and how big it is, in the block-entity render space.
 *
 * <p>Each concrete PIDS renderer ({@code RenderLCDPIDS}, {@code RenderRVPIDS}) receives this
 * geometry through its constructor, because the panel size differs per block. Handing it to
 * {@link PIDSLayout} lets a component layout be drawn onto exactly the rectangle the
 * built-in renderer uses for its background artwork.</p>
 *
 * <p>The field meanings mirror the constructor parameters the renderers already take:
 * {@link #scale} is the divisor applied by {@code matrices.scale(1F / scale, ...)}, and
 * {@link #panelWidth} / {@link #panelHeight} are the size of the background artwork in that
 * same local space. The panel's left edge is always centred on {@link #startX}
 * ({@code startX - panelWidth / 2} in both renderers); {@link #panelOffsetY} supplies the
 * per-renderer vertical origin.</p>
 */
public class PIDSGeometry {

	/** Panel centre X in the renderer's local space. */
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
	 * Y of the background artwork's top edge in the renderer's local space, i.e. the sum of
	 * the renderer's own {@code BACKGROUND_Y} translate and the shared {@code -1.5F} the
	 * background quad is drawn at. LCD PIDS uses {@code -2.5F}, RV PIDS {@code -11.0F}.
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
						float panelWidth, float panelHeight, float panelOffsetY,
						boolean rotate90, float rotation,
						String defaultFont, int defaultTextColor) {
		this.startX = startX;
		this.startY = startY;
		this.startZ = startZ;
		this.scale = scale;
		this.panelWidth = panelWidth;
		this.panelHeight = panelHeight;
		this.panelOffsetY = panelOffsetY;
		this.rotate90 = rotate90;
		this.rotation = rotation;
		this.defaultFont = defaultFont;
		this.defaultTextColor = defaultTextColor;
	}

	/** @return the panel's left edge in the renderer's local space. */
	public float panelLeft() {
		return startX - panelWidth / 2F;
	}
}
