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

        /* The list is drawn beside the field -- right of it, or left of it -- and never above or
         * below it.
         *
         * Below was the original behaviour, and it covered every row under the field: on both PIDS
         * config screens the arrival- and departure-switch rows sit there, and those rows are
         * separate widgets added after this one, so they render on top of the list and neither the
         * candidates nor their labels can be read.
         *
         * Beside it, the space is empty on the screens this field belongs to: on the two PIDS
         * config screens the checkboxes stop at PANEL_WIDTH (20 + 144) and the other text fields
         * sit at this field's own x, which is where the list used to be drawn; on the projector
         * screen the whole panel to the left of the field is empty.
         *
         * Above and below are gone for good. They only ever existed as the fallback for a side
         * without room for the list at its natural width, and what took the list there was always
         * a long preset id: the ids run past thirty characters -- gurigrui_japanese_departure_board
         * is one a pack ships -- which on the two config screens is wider than everything left
         * between the field and the right edge of the screen (232 + 3 + 192 against a 429 pixel
         * screen at GUI scale 2), so a rule that abandons the side as soon as the whole list does
         * not fit ends up above the field on the very screens this list is for. The width was the
         * problem, not the side: the list is narrowed to what the side actually has instead, and
         * the names are cut to fit it.
         *
         * So: the right whenever it can hold the whole list or at least MIN_LIST_W of it, else the
         * left on the same terms, and, when neither can, the wider of the two narrowed as far as it
         * goes. That last case wants a field that leaves less than MIN_LIST_W on both sides, which
         * none of the three screens can produce -- their fields are 160 or 182 wide and start at
         * least 20 pixels in -- and it is kept so that a screen narrow enough to manage it still
         * gets a list on one side of the field rather than one past the edge of the screen.
         *
         * Every case puts the list on the field's own line and clear of the field itself: it starts
         * GAP to the right of the box or ends GAP to its left, so the row it shares with the field
         * is only ever covered where that row is empty. */
        final int naturalListWidth = shown.stream().mapToInt(font::width).max().orElse(0) + 4;
        final int listHeight = shown.size() * font.lineHeight + 3;
        final int fieldX = UtilitiesClient.getWidgetX(this);
        final int fieldY = UtilitiesClient.getWidgetY(this);
        final int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        final int GAP = 3;          /* the gap the field has always had between itself and the list */
        final int EDGE = 2;         /* keep the border out of the outermost pixels of the screen */
        final int BORDER = 3;       /* the outline is drawn this far outside the text box */
        final int MIN_LIST_W = 60;  /* under this a side is not worth moving to; the other one is used */

        /* What each side can give the list: the distance from the field's own edge to the screen
           edge, less the gap to the field and less the outline and margin the list keeps off it. */
        final int rightX = fieldX + width + GAP;
        final int rightRoom = screenWidth - EDGE - BORDER - rightX;
        final int leftRoom = fieldX - GAP - EDGE - BORDER;

        final boolean onRight;
        final int listWidth;
        if (rightRoom >= Math.min(naturalListWidth, MIN_LIST_W)) {
            onRight = true;
            listWidth = Math.min(naturalListWidth, rightRoom);
        } else if (leftRoom >= Math.min(naturalListWidth, MIN_LIST_W)) {
            onRight = false;
            listWidth = Math.min(naturalListWidth, leftRoom);
        } else {
            /* Neither side can hold the list at MIN_LIST_W, so the wider side takes it narrowed to
               whatever it has -- a pixel at worst, so that there is something to draw. Should even
               that pixel not fit, the clamp below is all that keeps the list on the screen, and a
               list lying over the field's edge is then the only alternative to one drawn off it. */
            onRight = rightRoom >= leftRoom;
            listWidth = Math.max(1, Math.min(naturalListWidth, onRight ? rightRoom : leftRoom));
        }

        final int listY = fieldY;
        int listX = onRight ? rightX : fieldX - GAP - listWidth;
        if (listX < EDGE + BORDER || listX + listWidth > screenWidth - EDGE - BORDER) {
            /* The two sides fit by construction; only the case above can land here. */
            listX = Mth.clamp(listX, EDGE + BORDER, Math.max(EDGE + BORDER, screenWidth - EDGE - BORDER - listWidth));
        }

        guiGraphics.fill(listX - 2, listY - 2, listX + listWidth, listY + listHeight, 0xE0101010);
        guiGraphics.renderOutline(listX - 3, listY - 3, listWidth + 2, listHeight + 2, 0xFF909090);

        /* The names are cut to the list rather than the list stretched to the names: one
           31-character id would otherwise set the width and push the list off the screen. Only the
           drawing is cut -- shown itself is left alone, so Tab and Enter still write the whole id. */
        final int textWidth = listWidth - 2;
        for (int i = 0; i < shown.size(); i++) {
            final int color = i == highlight ? ChatFormatting.YELLOW.getColor() : ARGB_WHITE;
            guiGraphics.drawString(font, trimToWidth(font, shown.get(i), textWidth), listX, listY + 1 + i * font.lineHeight, color, false);
        }
    }

    /**
     * As much of {@code text} as fits in {@code maxWidth} pixels, ending in an ellipsis when it had
     * to be cut.
     *
     * <p>This is a drawing concern only: the value a candidate is worth when Tab or Enter takes it
     * is the candidate itself, never this.</p>
     */
    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        final String ellipsis = "…";
        final int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            /* Not even the ellipsis fits -- only the last resort above narrows the list that far.
               Cut without one rather than draw past the box. */
            return font.plainSubstrByWidth(text, Math.max(0, maxWidth));
        }
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }
}
