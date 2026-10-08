package com.jsblock.screen;

import mtr.mappings.UtilitiesClient;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

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
         * where the list used to be drawn.
         *
         * Being beside the field was only ever the first choice, though, and the second choice is
         * what has to go: on those two screens the field starts at fieldX = 20 + the widest label
         * + 6 and is 160 wide, so the list goes right only while the window is wider than about
         * fieldX + 163 + listWidth + 2 -- roughly 330 GUI-scaled pixels with the Chinese labels
         * and a 17-character preset id, and more for every longer id a pack ships. A small
         * window, a high GUI scale, or one long id is enough to miss that, and the old second
         * choice then drew the list back under the field. Flipping left cannot rescue these
         * screens either: there are only ~66 pixels left of the field, where the row labels are.
         *
         * So: right, then left, then above the field, and below it only when there is no room
         * above. Above rather than below because the row above is one more setting the player can
         * scroll away from, while the rows below are the arrival and departure switches this list
         * was complained about covering. Every branch keeps the list clear of the field itself --
         * never on the input box, and never on its row band when the list goes over or under it. */
        final int listWidth = shown.stream().mapToInt(font::width).max().orElse(0) + 4;
        final int listHeight = shown.size() * font.lineHeight + 3;
        final int fieldX = UtilitiesClient.getWidgetX(this);
        final int fieldY = UtilitiesClient.getWidgetY(this);
        final int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        final int GAP = 3;      /* the gap the field has always had between itself and the list */
        final int EDGE = 2;     /* keep the border out of the outermost pixels of the screen */
        final int BORDER = 3;   /* the outline is drawn this far outside the text box */

        int listX = fieldX + width + GAP;
        int listY = fieldY;
        if (listX + listWidth > screenWidth - EDGE) {
            final int flippedX = fieldX - GAP - listWidth;
            if (flippedX - BORDER >= EDGE) {
                /* Flipped, and 3 pixels clear of the field's left edge. On the projector screen
                   the whole panel to the left is empty; on the two PIDS config screens the row
                   labels are there, so this only happens when the list is short enough to fit in
                   what is left of the label column. */
                listX = flippedX;
            } else {
                /* Neither side has room for the whole list. Clamp the list into the screen and
                   lift it above the field's row; the row above is covered while the list is open,
                   which is the price of not covering the field the player is typing into. */
                listX = Mth.clamp(fieldX, EDGE + BORDER, Math.max(EDGE + BORDER, screenWidth - EDGE - listWidth));
                listY = fieldY - GAP - listHeight;
                if (listY - BORDER < EDGE) {
                    /* No room above either -- the field is at the top of the content. Below the
                       field is the original placement and the one this list must not fall back to
                       by default, but with no side and no room above it is the only space left. */
                    listY = fieldY + height + TEXT_FIELD_PADDING;
                }
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
