package com.jsblock.amberui.gui.widget;

import com.jsblock.amberui.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

import static com.jsblock.amberui.gui.widget.ListViewWidget.ENTRY_PADDING;

/**
 * Represent a row in {@link ListViewWidget}.<br>
 * Ported from JCM 2.3's {@code com.lx862.amberui.gui.widget.ContentItem}.
 */
public class ContentItem extends AbstractListItem {
	public DrawIconCallback drawIconCallback = null;
	public final Component title;
	public final WidgetHost widget;
	private final int height;

	public ContentItem(Component title, WidgetHost widget, int height) {
		this.height = height;
		this.title = title;
		this.widget = widget;
	}

	public ContentItem(Component title, WidgetHost widget) {
		this(title, widget, 22);
	}

	public ContentItem setIcon(ResourceLocation textureId) {
		this.drawIconCallback = (guiGraphics, startX, startY, width, height) -> GuiHelper.drawTexture(guiGraphics, textureId, startX, startY, width, height);
		return this;
	}

	public ContentItem setIconCallback(DrawIconCallback drawIconCallback) {
		this.drawIconCallback = drawIconCallback;
		return this;
	}

	public boolean hasIcon() {
		return this.drawIconCallback != null;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int entryX, int entryY, int width, int height, int mouseX, int mouseY, boolean widgetVisible, boolean rowInSight, double elapsed, float tickDelta) {
		super.draw(guiGraphics, entryX, entryY, width, height, mouseX, mouseY, widgetVisible, rowInSight, elapsed, tickDelta);
		drawListEntry(guiGraphics, entryX, entryY, width, mouseX, mouseY, widgetVisible, rowInSight, elapsed, tickDelta);
	}

	@Override
	public boolean matchQuery(String searchTerm) {
		return Objects.equals(searchTerm, "") || (title != null && title.getString().toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT)));
	}

	@Override
	public void positionChanged(int entryX, int entryY) {
		if(widget != null) {
			final int offsetY = (height - widget.getHeight()) / 2;
			widget.setX(entryX - widget.getWidth());
			widget.setY(entryY + offsetY);
		}
	}

	@Override
	public void hidden() {
		if(widget != null) {
			widget.setVisible(false);
		}
	}

	@Override
	public void shown() {
		if(widget != null) {
			widget.setVisible(true);
		}
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	private void drawListEntry(GuiGraphics guiGraphics, int entryX, int entryY, int width, int mouseX, int mouseY, boolean widgetVisible, boolean rowInSight, double elapsed, float tickDelta) {
		if(title != null && rowInSight) {
			drawListEntryDescription(guiGraphics, entryX, entryY, width, elapsed);
		}

		if(widget != null) {
			widget.setVisible(widgetVisible);

			if(rowInSight) {
				// We have to draw our widget (Right side) again after rendering the highlight so it doesn't get covered.
				widget.render(guiGraphics, mouseX, mouseY, tickDelta);
			}
		}
	}

	private void drawListEntryDescription(GuiGraphics guiGraphics, int entryX, int entryY, int width, double elapsed) {
		final int textHeight = 9;
		final int iconSize = hasIcon() ? height - ENTRY_PADDING : 0;
		final int widgetWidth = widget == null ? 0 : widget.getWidth();
		final int availableTextWidth = width - widgetWidth - ENTRY_PADDING - iconSize;
		final int textY = (height / 2) - (textHeight / 2);
		final boolean widgetEnabled = widget == null || widget.getActive();

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(entryX, entryY, 0);
		guiGraphics.pose().translate(ENTRY_PADDING, 0, 0);

		if(hasIcon()) {
			drawIconCallback.accept(guiGraphics, 0, ((height - iconSize) / 2), iconSize, iconSize);
			guiGraphics.pose().translate(iconSize + ENTRY_PADDING, 0, 0); // Shift the text to the right
		}

		final int textColor = widgetEnabled ? ARGB_WHITE : 0xFF888888;
		GuiHelper.drawScrollableText(guiGraphics, title, elapsed, entryX + ENTRY_PADDING + iconSize, 0, textY, availableTextWidth - iconSize - ENTRY_PADDING - ENTRY_PADDING - ENTRY_PADDING, textColor, true);
		guiGraphics.pose().popPose();
	}

	@FunctionalInterface
	public interface DrawIconCallback {
		void accept(GuiGraphics guiGraphics, int startX, int startY, int width, int height);
	}
}
