package com.jsblock.pids;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

/**
 * Bundle of the mutable render state a {@link PIDSComponent} needs, so component
 * signatures stay short.
 *
 * <p>JCM 2.x passes a {@code GraphicsHolder} plus an optional {@code GuiDrawing} and a
 * {@code Direction} to every component. MTR 3 draws through raw Minecraft primitives
 * ({@link PoseStack} + {@link MultiBufferSource}) plus MTR's {@code mtr.client.IDrawing}
 * helpers, so this holder carries those instead.</p>
 */
public class PIDSGraphics {

	/** Model-view stack the component should draw into (already positioned at the PIDS). */
	public final PoseStack matrices;
	/** Shared vertex consumers for batched geometry. */
	public final MultiBufferSource vertexConsumers;
	/** Immediate-mode buffer source required by the font renderer. */
	public final MultiBufferSource.BufferSource immediate;
	/** Block facing, for orienting quads. */
	public final Direction facing;
	/** Packed light value for emissive PIDS panels. */
	public final int light;
	/** Default text colour (ARGB) supplied by the preset or the block entity. */
	public final int textColor;
	/** Resource location of the font to draw text with. */
	public final String font;
	/** Multiplier converting "design units" into the units the caller draws in. */
	public final float scale;

	public PIDSGraphics(PoseStack matrices, MultiBufferSource vertexConsumers,
						MultiBufferSource.BufferSource immediate, Direction facing,
						int light, int textColor, String font, float scale) {
		this.matrices = matrices;
		this.vertexConsumers = vertexConsumers;
		this.immediate = immediate;
		this.facing = facing;
		this.light = light;
		this.textColor = textColor;
		this.font = font;
		this.scale = scale;
	}
}
