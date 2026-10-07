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
 * This text field just suggest stuff, autofill when enter or tab is pressed. <br>
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

    /**
     * The candidates Tab is cycling through, and where it is in them.
     *
     * <p>They cannot be read off {@link #matchedSuggestionList} while cycling: completing sets the
     * field to a candidate in full, and the responder then filters by prefix, so the only match
     * left is the one just typed and a second Tab would go nowhere. The list is therefore taken
     * once, before the first completion, and dropped again as soon as the player edits the text
     * by hand — which is also what resets vanilla's command suggestions.</p>
     */
    private List<String> cycleList = null;
    private int cycleIndex = 0;

    /** True while this widget is the one setting the value, so the responder can tell. */
    private boolean completing = false;

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

            if (!completing) {
                cycleList = null;
            }
            changedListener.accept(text);
        });
    }

    /**
     * {@code 258} is Tab, {@code 257} / {@code 335} are Enter and the numpad Enter.
     *
     * <p>Enter takes the suggestion the list is showing; Tab takes it too and then keeps walking
     * down the candidates on repeat. The preset field is the one place a player types an id by
     * hand, and the ids are long enough that neither key alone is pleasant.</p>
     */
    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (i == 258 && this.canConsumeInput() && !matchedSuggestionList.isEmpty()) {
            if (cycleList == null) {
                cycleList = new ArrayList<>(matchedSuggestionList);
                cycleIndex = 0;
            }
            if (cycleIndex >= cycleList.size()) {
                cycleIndex = 0;
            }
            completing = true;
            try {
                this.setValue(cycleList.get(cycleIndex));
            } finally {
                completing = false;
            }
            cycleIndex++;
            return true;
        }
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
        /* While Tab is cycling, show the whole cycle and mark the one Tab will take next; a
           plain prefix filter would collapse to the single completed id and the player would
           have no way to see that there is anything left to cycle through. */
        final List<String> shown = cycleList != null ? cycleList : matchedSuggestionList;
        final int highlight = cycleList == null || cycleList.isEmpty() ? 0 : cycleIndex % cycleList.size();
        if (!isFocused() || shown.isEmpty()) {
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
        final int listWidth = shown.stream().mapToInt(font::width).max().orElse(0) + 4;
        final int listHeight = shown.size() * font.lineHeight + 3;
        final int fieldX = UtilitiesClient.getWidgetX(this);
        final int fieldY = UtilitiesClient.getWidgetY(this);

        int listX = fieldX + width + 3;
        int listY = fieldY;
        if (listX + listWidth > Minecraft.getInstance().getWindow().getGuiScaledWidth() - 2) {
            /* Right first, then left, then below.
               
               Below is the original behaviour and it is the worst of the three: on both PIDS config
               screens and on the projector's, the fields sit directly under this one, so the list
               lands on top of them and neither can be read. The left is usually wide open — on the
               projector screen the whole panel is — so try that before giving up on the sides. */
            final int leftX = fieldX - listWidth - 3;
            if (leftX >= 2) {
                listX = leftX;
            } else {
                listX = fieldX;
                listY = fieldY + height + TEXT_FIELD_PADDING;
            }
        }

        guiGraphics.fill(listX - 2, listY - 2, listX + listWidth, listY + listHeight, 0xE0101010);
        guiGraphics.renderOutline(listX - 3, listY - 3, listWidth + 2, listHeight + 2, 0xFF909090);

        for (int i = 0; i < shown.size(); i++) {
            final int color = i == highlight ? ChatFormatting.YELLOW.getColor() : ARGB_WHITE;
            guiGraphics.drawString(font, shown.get(i), listX, listY + 1 + i * font.lineHeight, color, false);
        }
    }
}
