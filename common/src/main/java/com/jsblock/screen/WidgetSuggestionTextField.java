package com.jsblock.screen;

import mtr.mappings.UtilitiesClient;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This text field just suggest stuff, autofill when enter is pressed. <br>
 * Also make it red when it can't find any suggestion
 * @author LX86
 * @since 1.1.4
 * @see WidgetBetterTextField
 */
public class WidgetSuggestionTextField extends WidgetBetterTextField {
    private final Collection<String> suggestionList;
    private List<String> matchedSuggestionList;
    private final int RED_COLOR = 16733525;
    private final int WHITE_COLOR = 16777215;
    private String currentSuggestion = "";

    public WidgetSuggestionTextField(String defaultSuggestion, Collection<String> suggestionList, int maxLength, boolean strict) {
        super(defaultSuggestion, maxLength);
        this.suggestionList = suggestionList;
        this.matchedSuggestionList = new ArrayList<>(suggestionList);
    }

    @Override
    public void setResponder(Consumer<String> changedListener) {
        super.setResponder(text -> {
            matchedSuggestionList = text.isEmpty() ? new ArrayList<>(suggestionList) : suggestionList.stream().filter(str -> str.startsWith(text)).collect(Collectors.toList());

            if(matchedSuggestionList.isEmpty()) {
                this.setTextColor(RED_COLOR);
            } else {
                this.setTextColor(WHITE_COLOR);
            }

            if(!text.isEmpty() && !matchedSuggestionList.isEmpty()) {
                setSuggestion(matchedSuggestionList.get(0).substring(text.length()));
                currentSuggestion = matchedSuggestionList.get(0);
            }
            changedListener.accept(text);
        });
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (this.canConsumeInput() && !this.getValue().isEmpty()) {
            /* 257 / 335 = Enter */
            if(i == 257 || i == 335) {
                this.setValue(currentSuggestion);
            }
        }
        return super.keyPressed(i, j, k);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (!isFocused() || matchedSuggestionList.isEmpty()) {
            return;
        }
        final Font font = Minecraft.getInstance().font;

        /* The list is drawn beside the field, not under it.
         *
         * Under it was the original behaviour, and it covered every row below -- on both PIDS
         * config screens the arrival- and departure-switch rows sit there. Those rows are
         * separate widgets added after this one, so they render on top of the list and neither
         * the candidates nor their labels can be read.
         *
         * Beside it, the space is empty on both screens: their checkboxes stop at
         * PANEL_WIDTH (20 + 144) and the other text fields sit at this field's own x, which is
         * where the list used to be drawn. */
        final int listWidth = matchedSuggestionList.stream().mapToInt(font::width).max().orElse(0) + 4;
        final int listHeight = matchedSuggestionList.size() * font.lineHeight + 3;
        final int fieldX = UtilitiesClient.getWidgetX(this);
        final int fieldY = UtilitiesClient.getWidgetY(this);

        int listX = fieldX + width + 3;
        int listY = fieldY;
        if (listX + listWidth > Minecraft.getInstance().getWindow().getGuiScaledWidth() - 2) {
            /* No room to the right -- underneath is the old behaviour, but with a background
               this time, so whatever it lands on does not turn into unreadable overlap. */
            listX = fieldX;
            listY = fieldY + height + TEXT_FIELD_PADDING;
        }

        guiGraphics.fill(listX - 2, listY - 2, listX + listWidth, listY + listHeight, 0xE0101010);
        guiGraphics.renderOutline(listX - 3, listY - 3, listWidth + 2, listHeight + 2, 0xFF909090);

        for (int i = 0; i < matchedSuggestionList.size(); i++) {
            final int color = i == 0 ? ChatFormatting.YELLOW.getColor() : ARGB_WHITE;
            guiGraphics.drawString(font, matchedSuggestionList.get(i), listX, listY + 1 + i * font.lineHeight, color, false);
        }
    }
}
