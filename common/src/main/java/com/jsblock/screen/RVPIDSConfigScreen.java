package com.jsblock.screen;

import com.jsblock.block.PIDSRVBase;
import com.jsblock.client.JobanCustomResources;
import com.jsblock.packet.PacketClient;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import mtr.packet.IPacket;
import mtr.screen.PIDSConfigScreen;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;

public class RVPIDSConfigScreen extends ScreenMapper implements IGui, IPacket {

    private final BlockPos pos1;
    private final BlockPos pos2;
    private final String[] messages;
    private final boolean[] hideArrival;
    private String presetID;
    private boolean hidePlatformNumber;
    private boolean autoSwitchEnabled;
    private String autoSwitchPreset;
    private int autoSwitchCountdown;
    private int autoSwitchDuration;
    private boolean depAutoSwitchEnabled;
    private String depAutoSwitchPreset;
    private int depAutoSwitchCountdown;
    private int depAutoSwitchDuration;
    private boolean depAutoSwitchUntilClose;

    private final WidgetBetterTextField[] textFieldMessages;
    private final WidgetBetterCheckbox[] buttonsHideArrival;
    private final WidgetBetterCheckbox buttonsHidePlatformNumbers;
    private final WidgetSuggestionTextField presetIDTextField;
    private final WidgetBetterCheckbox autoSwitchCheckbox;
    private final WidgetSuggestionTextField autoSwitchPresetTextField;
    private final WidgetBetterTextField autoSwitchCountdownTextField;
    private final WidgetBetterTextField autoSwitchDurationTextField;
    private final WidgetBetterCheckbox depAutoSwitchCheckbox;
    private final WidgetBetterCheckbox depAutoSwitchUntilCloseCheckbox;
    private final WidgetSuggestionTextField depAutoSwitchPresetTextField;
    private final WidgetBetterTextField depAutoSwitchCountdownTextField;
    private final WidgetBetterTextField depAutoSwitchDurationTextField;
    private final WidgetBetterCheckbox selectAllCheckbox;
    private final Button filterButton;
    private final Set<Long> filterPlatformIds;

    private final Component messageText = Text.translatable("gui.mtr.pids_message");
    private final Component hideArrivalText = Text.translatable("gui.mtr.hide_arrival");
    private final Component hidePlatformNumberText = Text.translatable("gui.jsblock.hide_platform_number");
    private final Component presetText = Text.translatable("gui.jsblock.pids_preset");
    private final Component arrSectionText = Text.translatable("gui.jsblock.arrival_switch_section");
    private final Component autoSwitchText = Text.translatable("gui.jsblock.auto_switch_enabled");
    private final Component autoSwitchPresetText = Text.translatable("gui.jsblock.auto_switch_preset");
    private final Component autoSwitchCountdownText = Text.translatable("gui.jsblock.auto_switch_countdown");
    private final Component autoSwitchDurationText = Text.translatable("gui.jsblock.auto_switch_duration");
    private final Component depSectionText = Text.translatable("gui.jsblock.departure_switch_section");
    private final Component depAutoSwitchText = Text.translatable("gui.jsblock.dep_auto_switch_enabled");
    private final Component depAutoSwitchPresetText = Text.translatable("gui.jsblock.dep_auto_switch_preset");
    private final Component depAutoSwitchCountdownText = Text.translatable("gui.jsblock.dep_auto_switch_countdown");
    private final Component depAutoSwitchDurationText = Text.translatable("gui.jsblock.dep_auto_switch_duration");
    private final Component depAutoSwitchUntilCloseText = Text.translatable("gui.jsblock.dep_auto_switch_until_close");

    private double scrollAmount = 0;
    private double targetScrollAmount = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private static final int SCROLL_SPEED = 18;
    private static final double LERP_FACTOR = 0.22;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int ARGB_YELLOW = 0xFFFFFF00;

    private static final int MAX_MESSAGE_LENGTH = 2048;

    private static final int ROW_H = SQUARE_SIZE + TEXT_FIELD_PADDING + 6;
    private static final int SECTION_GAP = ROW_H + 8;
    private static final int NUM_FIELD_W = 52;
    private static final int PRESET_FIELD_W = 160;

    private int fieldX;
    private int presetY;
    private int arrCheckY, arrPresetY, arrNumY, arrDurY;
    private int depCheckY, depPresetY, depNumY, depDurY, depUntilY;
    private int hidePlatY;

    public RVPIDSConfigScreen(BlockPos pos1, BlockPos pos2, int maxArrivals,
                              String[] packetMessages, boolean[] packetRowHidden, Set<Long> packetPlatformIds,
                              boolean hidePlatformNumber, String presetID,
                              boolean autoSwitchEnabled, String autoSwitchPreset, int autoSwitchCountdown, int autoSwitchDuration,
                              boolean depAutoSwitchEnabled, String depAutoSwitchPreset, int depAutoSwitchCountdown, int depAutoSwitchDuration, boolean depAutoSwitchUntilClose) {
        super(Text.literal(""));
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.hidePlatformNumber = hidePlatformNumber;
        this.presetID = presetID;
        this.autoSwitchEnabled = autoSwitchEnabled;
        this.autoSwitchPreset = autoSwitchPreset;
        this.autoSwitchCountdown = autoSwitchCountdown > 0 ? autoSwitchCountdown : 10;
        this.autoSwitchDuration = autoSwitchDuration > 0 ? autoSwitchDuration : 10;
        this.depAutoSwitchEnabled = depAutoSwitchEnabled;
        this.depAutoSwitchPreset = depAutoSwitchPreset;
        this.depAutoSwitchCountdown = depAutoSwitchCountdown > 0 ? depAutoSwitchCountdown : 10;
        this.depAutoSwitchDuration = depAutoSwitchDuration > 0 ? depAutoSwitchDuration : 10;
        this.depAutoSwitchUntilClose = depAutoSwitchUntilClose;

        messages = new String[maxArrivals];
        for (int i = 0; i < maxArrivals; i++) messages[i] = "";
        hideArrival = new boolean[maxArrivals];
        filterPlatformIds = new HashSet<>();

        textFieldMessages = new WidgetBetterTextField[maxArrivals];
        for (int i = 0; i < maxArrivals; i++)
            textFieldMessages[i] = new WidgetBetterTextField("", MAX_MESSAGE_LENGTH);

        selectAllCheckbox = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE, Text.translatable("gui.mtr.automatically_detect_nearby_platform"), checked -> {});

        presetIDTextField = new WidgetSuggestionTextField("None", JobanCustomResources.PIDSPresets.keySet(), MAX_MESSAGE_LENGTH, true);

        autoSwitchCheckbox = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE, autoSwitchText, checked -> {});
        autoSwitchPresetTextField = new WidgetSuggestionTextField("None", JobanCustomResources.PIDSPresets.keySet(), MAX_MESSAGE_LENGTH, true);
        autoSwitchCountdownTextField = new WidgetBetterTextField(String.valueOf(this.autoSwitchCountdown), 4);
        autoSwitchDurationTextField = new WidgetBetterTextField(String.valueOf(this.autoSwitchDuration), 4);

        depAutoSwitchCheckbox = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE, depAutoSwitchText, checked -> {});
        depAutoSwitchPresetTextField = new WidgetSuggestionTextField("None", JobanCustomResources.PIDSPresets.keySet(), MAX_MESSAGE_LENGTH, true);
        depAutoSwitchCountdownTextField = new WidgetBetterTextField(String.valueOf(this.depAutoSwitchCountdown), 4);
        depAutoSwitchDurationTextField = new WidgetBetterTextField(String.valueOf(this.depAutoSwitchDuration), 4);
        depAutoSwitchUntilCloseCheckbox = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE, depAutoSwitchUntilCloseText, checked -> {});

        buttonsHideArrival = new WidgetBetterCheckbox[maxArrivals];
        buttonsHidePlatformNumbers = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE, Text.literal(""), checked -> {});
        for (int i = 0; i < maxArrivals; i++)
            buttonsHideArrival[i] = new WidgetBetterCheckbox(0, 0, 0, SQUARE_SIZE, hideArrivalText, checked -> {});

        /* What the server sent with the packet first, the client's own copy of the block entity only
           as a fallback. Reading the entity alone meant a screen could open blank whenever that copy
           had not caught up, and closing it then sent the blanks back -- which is how a configured
           board lost its text. JCM 2.x sends the data in the packet for the same reason. */
        if (packetPlatformIds != null) {
            filterPlatformIds.addAll(packetPlatformIds);
        }
        for (int i = 0; i < maxArrivals && packetMessages != null && i < packetMessages.length; i++) {
            messages[i] = packetMessages[i] == null ? "" : packetMessages[i];
            if (packetRowHidden != null && i < packetRowHidden.length) {
                hideArrival[i] = packetRowHidden[i];
            }
        }
        final Level world = Minecraft.getInstance().level;
        if (packetMessages == null && world != null) {
            final BlockEntity entity = world.getBlockEntity(pos1);
            if (entity instanceof PIDSRVBase.TileEntityBlockRVPIDS) {
                filterPlatformIds.addAll(((PIDSRVBase.TileEntityBlockRVPIDS) entity).getPlatformIds());
                for (int i = 0; i < maxArrivals; i++) {
                    messages[i] = ((PIDSRVBase.TileEntityBlockRVPIDS) entity).getMessage(i);
                    hideArrival[i] = ((PIDSRVBase.TileEntityBlockRVPIDS) entity).getHideArrival(i);
                }
            }
        }
        filterButton = PIDSConfigScreen.getPlatformFilterButton(pos1, selectAllCheckbox, filterPlatformIds, this);
    }

    @Override
    protected void init() {
        super.init();
        final int textWidth = font.width(hideArrivalText) + SQUARE_SIZE + TEXT_PADDING * 2;
        final int labelW = Math.max(font.width(autoSwitchPresetText), font.width(depAutoSwitchPresetText));
        fieldX = SQUARE_SIZE + labelW + TEXT_PADDING;
        int y = SQUARE_SIZE;

        IDrawing.setPositionAndWidth(selectAllCheckbox, SQUARE_SIZE, y, PANEL_WIDTH);
        selectAllCheckbox.setChecked(filterPlatformIds.isEmpty());
        addDrawableChild(selectAllCheckbox);
        y += SQUARE_SIZE;

        IDrawing.setPositionAndWidth(filterButton, SQUARE_SIZE, y, PANEL_WIDTH / 2);
        filterButton.setMessage(Text.translatable("selectWorld.edit"));
        addDrawableChild(filterButton);
        y += SQUARE_SIZE * 2 + TEXT_PADDING;

        for (int i = 0; i < textFieldMessages.length; i++) {
            IDrawing.setPositionAndWidth(textFieldMessages[i], SQUARE_SIZE + TEXT_FIELD_PADDING / 2, y, width - SQUARE_SIZE * 2 - TEXT_FIELD_PADDING - textWidth);
            textFieldMessages[i].setValue(messages[i]);
            addDrawableChild(textFieldMessages[i]);

            IDrawing.setPositionAndWidth(buttonsHideArrival[i], width - SQUARE_SIZE - textWidth + TEXT_PADDING, y, textWidth);
            buttonsHideArrival[i].setChecked(hideArrival[i]);
            addDrawableChild(buttonsHideArrival[i]);

            y += ROW_H;
        }

        y += TEXT_PADDING;

        hidePlatY = y;
        IDrawing.setPositionAndWidth(buttonsHidePlatformNumbers, width - SQUARE_SIZE - textWidth + TEXT_PADDING, y, textWidth);
        buttonsHidePlatformNumbers.setChecked(hidePlatformNumber);
        addDrawableChild(buttonsHidePlatformNumbers);
        y += ROW_H;

        presetY = y;
        IDrawing.setPositionAndWidth(presetIDTextField, fieldX, y, PRESET_FIELD_W);
        presetIDTextField.setValue(presetID);
        addDrawableChild(presetIDTextField);
        y += ROW_H + SECTION_GAP;

        arrCheckY = y;
        IDrawing.setPositionAndWidth(autoSwitchCheckbox, SQUARE_SIZE, y, PANEL_WIDTH);
        autoSwitchCheckbox.setChecked(autoSwitchEnabled);
        addDrawableChild(autoSwitchCheckbox);
        y += ROW_H;

        arrPresetY = y;
        IDrawing.setPositionAndWidth(autoSwitchPresetTextField, fieldX, y, PRESET_FIELD_W);
        autoSwitchPresetTextField.setValue(autoSwitchPreset);
        addDrawableChild(autoSwitchPresetTextField);
        y += ROW_H;

        arrNumY = y;
        IDrawing.setPositionAndWidth(autoSwitchCountdownTextField, fieldX, y, NUM_FIELD_W);
        autoSwitchCountdownTextField.setValue(String.valueOf(autoSwitchCountdown));
        addDrawableChild(autoSwitchCountdownTextField);
        y += ROW_H;

        arrDurY = y;
        IDrawing.setPositionAndWidth(autoSwitchDurationTextField, fieldX, y, NUM_FIELD_W);
        autoSwitchDurationTextField.setValue(String.valueOf(autoSwitchDuration));
        addDrawableChild(autoSwitchDurationTextField);
        y += ROW_H + SECTION_GAP;

        depCheckY = y;
        IDrawing.setPositionAndWidth(depAutoSwitchCheckbox, SQUARE_SIZE, y, PANEL_WIDTH);
        depAutoSwitchCheckbox.setChecked(depAutoSwitchEnabled);
        addDrawableChild(depAutoSwitchCheckbox);
        y += ROW_H;

        depPresetY = y;
        IDrawing.setPositionAndWidth(depAutoSwitchPresetTextField, fieldX, y, PRESET_FIELD_W);
        depAutoSwitchPresetTextField.setValue(depAutoSwitchPreset);
        addDrawableChild(depAutoSwitchPresetTextField);
        y += ROW_H;

        depNumY = y;
        IDrawing.setPositionAndWidth(depAutoSwitchCountdownTextField, fieldX, y, NUM_FIELD_W);
        depAutoSwitchCountdownTextField.setValue(String.valueOf(depAutoSwitchCountdown));
        addDrawableChild(depAutoSwitchCountdownTextField);
        y += ROW_H;

        depDurY = y;
        IDrawing.setPositionAndWidth(depAutoSwitchDurationTextField, fieldX, y, NUM_FIELD_W);
        depAutoSwitchDurationTextField.setValue(String.valueOf(depAutoSwitchDuration));
        depAutoSwitchDurationTextField.setEditable(!depAutoSwitchUntilClose);
        addDrawableChild(depAutoSwitchDurationTextField);
        y += ROW_H;

        depUntilY = y;
        IDrawing.setPositionAndWidth(depAutoSwitchUntilCloseCheckbox, SQUARE_SIZE, y, PANEL_WIDTH);
        depAutoSwitchUntilCloseCheckbox.setChecked(depAutoSwitchUntilClose);
        addDrawableChild(depAutoSwitchUntilCloseCheckbox);

        contentHeight = y + SQUARE_SIZE + TEXT_PADDING * 2;
        maxScroll = Math.max(0, contentHeight - height);
    }

    @Override
    public void onClose() {
        for (int i = 0; i < textFieldMessages.length; i++) {
            messages[i] = textFieldMessages[i].getValue();
            hideArrival[i] = buttonsHideArrival[i].selected();
        }
        if (selectAllCheckbox.selected()) filterPlatformIds.clear();
        hidePlatformNumber = buttonsHidePlatformNumbers.selected();
        presetID = presetIDTextField.getValue();
        autoSwitchEnabled = autoSwitchCheckbox.selected();
        autoSwitchPreset = autoSwitchPresetTextField.getValue();
        depAutoSwitchEnabled = depAutoSwitchCheckbox.selected();
        depAutoSwitchPreset = depAutoSwitchPresetTextField.getValue();
        depAutoSwitchUntilClose = depAutoSwitchUntilCloseCheckbox.selected();

        try { autoSwitchCountdown = Integer.parseInt(autoSwitchCountdownTextField.getValue()); } catch (NumberFormatException e) { autoSwitchCountdown = 10; }
        try { autoSwitchDuration = Integer.parseInt(autoSwitchDurationTextField.getValue()); } catch (NumberFormatException e) { autoSwitchDuration = 10; }
        try { depAutoSwitchCountdown = Integer.parseInt(depAutoSwitchCountdownTextField.getValue()); } catch (NumberFormatException e) { depAutoSwitchCountdown = 10; }
        try { depAutoSwitchDuration = Integer.parseInt(depAutoSwitchDurationTextField.getValue()); } catch (NumberFormatException e) { depAutoSwitchDuration = 10; }

        PacketClient.sendRVPIDSConfigC2S(pos1, pos2, messages, hideArrival, filterPlatformIds, hidePlatformNumber, presetID,
                autoSwitchEnabled, autoSwitchPreset, autoSwitchCountdown, autoSwitchDuration,
                depAutoSwitchEnabled, depAutoSwitchPreset, depAutoSwitchCountdown, depAutoSwitchDuration, depAutoSwitchUntilClose);
        super.onClose();
    }

    @Override
    public void tick() {
        for (WidgetBetterTextField f : textFieldMessages) f.tick();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (maxScroll > 0) {
            targetScrollAmount = Math.max(0, Math.min(targetScrollAmount - amount * SCROLL_SPEED, maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (maxScroll > 0 && btn == 0 && mx >= width - SCROLLBAR_WIDTH - 4 && mx <= width) {
            isDraggingScrollbar = true;
            final int bh = Math.max(16, (int) (height * (double) height / contentHeight));
            targetScrollAmount = Math.max(0, Math.min(1, (my - bh / 2.0) / (height - bh))) * maxScroll;
            return true;
        }
        return super.mouseClicked(mx, my + scrollAmount, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (isDraggingScrollbar) { isDraggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my + scrollAmount, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isDraggingScrollbar && maxScroll > 0) {
            final int bh = Math.max(16, (int) (height * (double) height / contentHeight));
            targetScrollAmount = Math.max(0, Math.min(1, (my - bh / 2.0) / (height - bh))) * maxScroll;
            return true;
        }
        return super.mouseDragged(mx, my + scrollAmount, btn, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        try {
            renderBackground(g);

            scrollAmount += (targetScrollAmount - scrollAmount) * LERP_FACTOR;
            if (Math.abs(targetScrollAmount - scrollAmount) < 0.15) scrollAmount = targetScrollAmount;

            final int adjMy = (int) (mouseY + scrollAmount);
            final int shGap = font.lineHeight + 10;
            final int baseY = (SQUARE_SIZE - font.lineHeight) / 2;
            final int msgLabelY = SQUARE_SIZE * 4 + TEXT_PADDING - font.lineHeight - 4;

            g.enableScissor(0, 0, width, height);
            g.pose().pushPose();
            g.pose().translate(0, -scrollAmount, 0);

            g.drawString(font, Text.translatable("gui.mtr.filtered_platforms", selectAllCheckbox.selected() ? 0 : filterPlatformIds.size()), SQUARE_SIZE + filterButton.getWidth() + TEXT_PADDING, SQUARE_SIZE * 2 + TEXT_PADDING, ARGB_WHITE);
            g.drawString(font, messageText, SQUARE_SIZE, msgLabelY, ARGB_WHITE);
            g.drawString(font, hidePlatformNumberText, SQUARE_SIZE, hidePlatY + baseY, ARGB_WHITE);
            g.drawString(font, presetText, fieldX - font.width(presetText) - 4, presetY + baseY, ARGB_WHITE);

            g.drawString(font, arrSectionText, SQUARE_SIZE, arrCheckY - shGap, ARGB_YELLOW);
            g.drawString(font, autoSwitchPresetText, fieldX - font.width(autoSwitchPresetText) - 4, arrPresetY + baseY, ARGB_WHITE);
            g.drawString(font, autoSwitchCountdownText, fieldX - font.width(autoSwitchCountdownText) - 4, arrNumY + baseY, ARGB_WHITE);
            g.drawString(font, autoSwitchDurationText, fieldX - font.width(autoSwitchDurationText) - 4, arrDurY + baseY, ARGB_WHITE);

            g.drawString(font, depSectionText, SQUARE_SIZE, depCheckY - shGap, ARGB_YELLOW);
            g.drawString(font, depAutoSwitchPresetText, fieldX - font.width(depAutoSwitchPresetText) - 4, depPresetY + baseY, ARGB_WHITE);
            g.drawString(font, depAutoSwitchCountdownText, fieldX - font.width(depAutoSwitchCountdownText) - 4, depNumY + baseY, ARGB_WHITE);
            g.drawString(font, depAutoSwitchDurationText, fieldX - font.width(depAutoSwitchDurationText) - 4, depDurY + baseY, ARGB_WHITE);

            super.render(g, mouseX, adjMy, delta);

            g.pose().popPose();
            g.disableScissor();

            if (maxScroll > 0) {
                final int bh = Math.max(16, (int) (height * (double) height / contentHeight));
                final int by = (int) ((height - bh) * (scrollAmount / (double) maxScroll));
                g.fill(width - SCROLLBAR_WIDTH, 0, width, height, 0x18FFFFFF);
                g.fill(width - SCROLLBAR_WIDTH, by, width, by + bh, 0x88FFFFFF);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public boolean isPauseScreen() { return false; }
}
