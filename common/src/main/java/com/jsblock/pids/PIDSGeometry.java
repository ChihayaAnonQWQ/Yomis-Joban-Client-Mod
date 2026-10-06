package com.jsblock.pids;

/**
 * Describes where a PIDS panel sits and how big it is, in the block-entity render space.
 *
 * <p>Each concrete PIDS renderer ({@code RenderLCDPIDS}, {@code RenderRVPIDS}) receives this
 * geometry through its constructor, because the panel size differs per block. Handing it to
 * {@link PIDSLayout} lets a component layout be drawn without each renderer duplicating the
 * matrix maths.</p>
 *
 * <p>The field meanings mirror the constructor parameters the renderers already take:
 * {@link #scale} is the divisor applied by {@code matrices.scale(1F / scale, ...)}, and
 * {@link #panelWidth} / {@link #panelHeight} are the size of the background artwork in that
 * same local space.</p>
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
	/** Whether the panel is rotated a further 90 degrees. */
	public final boolean rotate90;
	/** Extra X rotation applied to the panel. */
	public final float rotation;
	/** Font resource location used when a preset does not specify one. */
	public final String defaultFont;
	/** Text colour used when a preset does not specify one. */
	public final int defaultTextColor;

	public PIDSGeometry(float startX, float startY, float startZ, float scale,
						float panelWidth, float panelHeight, boolean rotate90, float rotation,
						String defaultFont, int defaultTextColor) {
		this.startX = startX;
		this.startY = startY;
		this.startZ = startZ;
		this.scale = scale;
		this.panelWidth = panelWidth;
		this.panelHeight = panelHeight;
		this.rotate90 = rotate90;
		this.rotation = rotation;
		this.defaultFont = defaultFont;
		this.defaultTextColor = defaultTextColor;
	}
}
