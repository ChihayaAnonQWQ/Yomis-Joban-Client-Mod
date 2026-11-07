package com.jsblock.amberui.gui;

import com.jsblock.vermappings.render.RenderHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Shared GUI drawing helpers, ported from JCM 2.3's {@code com.lx862.amberui.gui.GuiHelper}.
 */
public interface GuiHelper {
	int MAX_CONTENT_WIDTH = 400;
	int BOTTOM_ROW_MARGIN = 6;
	int MAX_BUTTON_WIDTH = 375;
	int MAX_ALPHA = 0xFF000000;

	int LINE_HEIGHT = 9;
	int SQUARE_SIZE = 20;
	int TEXT_HEIGHT = 8;
	int ARGB_WHITE = 0xFFFFFFFF;
	int MAX_RENDER_LIGHT = 15728880;

	static Font getFont() {
		return Minecraft.getInstance().font;
	}

	static void drawTexture(GuiGraphics guiGraphics, ResourceLocation identifier, double x, double y, double width, double height) {
		drawTexture(guiGraphics, identifier, x, y, width, height, 0, 0, 1, 1);
	}

	static void drawTexture(GuiGraphics guiGraphics, ResourceLocation identifier, double x, double y, double width, double height, float u1, float v1, float u2, float v2) {
		if (identifier == null) {
			/* Callers may legitimately have no texture (a preset with neither thumbnail nor
			   background). Drawing nothing is correct; passing null on to the texture manager is a
			   crash inside RenderSystem.setShaderTexture. */
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderHelper.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderHelper.setTexture(Minecraft.getInstance(), identifier);

		final Tesselator tesselator = Tesselator.getInstance();
		final BufferBuilder bufferBuilder = tesselator.getBuilder();
		final Matrix4f pose = guiGraphics.pose().last().pose();

		bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		bufferBuilder.vertex(pose, (float) x, (float) (y + height), 0).uv(u1, v2).color(255, 255, 255, 255).endVertex();
		bufferBuilder.vertex(pose, (float) (x + width), (float) (y + height), 0).uv(u2, v2).color(255, 255, 255, 255).endVertex();
		bufferBuilder.vertex(pose, (float) (x + width), (float) y, 0).uv(u2, v1).color(255, 255, 255, 255).endVertex();
		bufferBuilder.vertex(pose, (float) x, (float) y, 0).uv(u1, v1).color(255, 255, 255, 255).endVertex();
		tesselator.end();
	}

	static void drawRectangle(GuiGraphics guiGraphics, double x, double y, double width, double height, int color) {
		guiGraphics.fill((int) Math.round(x), (int) Math.round(y), (int) Math.round(x + width), (int) Math.round(y + height), color);
	}

	static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
		guiGraphics.fill(x, y, x + width, y + 1, color);
		guiGraphics.fill(x, y, x + 1, y + height, color);
		guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
		guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
	}

	/**
	 * Draw text that shifts back and forth if there is not enough space to display it.
	 * Similar to the scrollable text added in Minecraft 1.19.4.
	 */
	static void drawScrollableText(GuiGraphics guiGraphics, Component text, double elapsed, int startX, int textX, int textY, int maxW, int color, boolean shadow) {
		final int textWidth = getFont().width(text);

		if(textWidth > maxW) {
			final double slideProgress = ((Math.sin(elapsed / 4)) / 2) + 0.5;
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(-slideProgress * (textWidth - maxW), 0, 0);
			ClipStack.add(startX, 0, maxW, 500);
			guiGraphics.drawString(getFont(), text, textX, textY, color, shadow);
			ClipStack.pop();
			guiGraphics.pose().popPose();
		} else {
			guiGraphics.drawString(getFont(), text, textX, textY, color, shadow);
		}
	}

	/**
	 * Scale the current matrix so that the given text fits within {@code maxWidth}.
	 */
	static void scaleToFit(GuiGraphics guiGraphics, int textWidth, double maxWidth, boolean scaleOnWidth) {
		if(textWidth > maxWidth && scaleOnWidth) {
			final double scale = maxWidth / textWidth;
			guiGraphics.pose().scale((float) scale, (float) scale, (float) scale);
		}
	}

	static int getButtonWidth(double width) {
		return (int) Math.min(MAX_BUTTON_WIDTH, width);
	}

	default boolean inRectangle(double targetX, double targetY, int rectX, int rectY, int rectW, int rectH) {
		return (targetX >= rectX && targetX <= rectX + rectW) && (targetY >= rectY && targetY <= rectY + rectH);
	}
}
