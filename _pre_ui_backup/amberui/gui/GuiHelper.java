package com.jsblock.amberui.gui;

import com.jsblock.amberui.gui.widget.MappedWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing helpers shared by the AmberUI screens.
 *
 * <p>The MTR 4 original drew through {@code GraphicsHolder} / {@code GuiDrawing}; MTR 3 draws
 * straight onto {@link GuiGraphics}, so every helper takes one. The coordinate arguments stay
 * doubles, as in the original, because the layout arithmetic in the list rows is fractional.</p>
 */
public interface GuiHelper {
	int MAX_CONTENT_WIDTH = 400;
	int BOTTOM_ROW_MARGIN = 6;
	int MAX_BUTTON_WIDTH = 375;
	int MAX_ALPHA = 0xFF000000;
	int ARGB_WHITE = 0xFFFFFFFF;

	/**
	 * Draws a whole texture scaled into the given rectangle.
	 *
	 * <p>{@code blit} with the texture size set to the destination size samples the whole 0..1 UV
	 * range, so this works for thumbnails of any pixel size without having to look the size up.</p>
	 */
	static void drawTexture(GuiGraphics guiGraphics, ResourceLocation identifier, double x, double y, double width, double height) {
		final int w = (int) Math.round(width);
		final int h = (int) Math.round(height);
		if (w <= 0 || h <= 0) {
			return;
		}
		guiGraphics.blit(identifier, (int) Math.round(x), (int) Math.round(y), 0, 0, w, h, w, h);
	}

	static void drawRectangle(GuiGraphics guiGraphics, double x, double y, double width, double height, int color) {
		guiGraphics.fill((int) Math.round(x), (int) Math.round(y),
				(int) Math.round(x + width), (int) Math.round(y + height), color);
	}

	/**
	 * Draws text that slides back and forth when it does not fit, like vanilla's scrolling string.
	 *
	 * @param startX the left edge of the clipping rectangle
	 * @param textX  the x the text would be drawn at without sliding
	 */
	static void drawScrollableText(GuiGraphics guiGraphics, Component text, double elapsed, int startX, int textX, int textY, int maxW, int color, boolean shadow) {
		final Font font = Minecraft.getInstance().font;
		final int textWidth = font.width(text);

		guiGraphics.pose().pushPose();
		if (textWidth > maxW) {
			final double slideProgress = (Math.sin(elapsed / 4) / 2) + 0.5;
			guiGraphics.pose().translate(-slideProgress * (textWidth - maxW), 0, 0);
			ClipStack.add(guiGraphics, startX, 0, maxW, 500);
			guiGraphics.drawString(font, text, textX, textY, color, shadow);
			ClipStack.pop(guiGraphics);
		} else {
			guiGraphics.drawString(font, text, textX, textY, color, shadow);
		}
		guiGraphics.pose().popPose();
	}

	/**
	 * Clamps a button width to {@link #MAX_BUTTON_WIDTH}; MTR 3's button texture is nine-sliced but
	 * the original limit is kept so screens laid out against AmberUI do not change size.
	 */
	static int getButtonWidth(double width) {
		return (int) Math.min(MAX_BUTTON_WIDTH, width);
	}

	default boolean inRectangle(double targetX, double targetY, int rectX, int rectY, int rectW, int rectH) {
		return (targetX >= rectX && targetX <= rectX + rectW) && (targetY >= rectY && targetY <= rectY + rectH);
	}

	static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
		guiGraphics.fill(x, y, x + width, y + 1, color);
		guiGraphics.fill(x, y, x + 1, y + height, color);
		guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
		guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
	}

	/** Convenience for the list rows: the real widgets a row owns, ready for {@code addDrawableChild}. */
	static java.util.List<net.minecraft.client.gui.components.AbstractWidget> widgetsOf(MappedWidget widget) {
		return MappedWidget.collect(widget);
	}
}
