package com.jsblock.screen;

import com.jsblock.Joban;
import com.jsblock.amberui.gui.GuiHelper;
import com.jsblock.amberui.gui.screen.TitledScreen;
import com.jsblock.amberui.gui.widget.ListViewWidget;
import com.jsblock.amberui.gui.widget.MappedWidget;
import com.jsblock.amberui.gui.widget.WidgetSet;
import com.jsblock.client.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mtr.mappings.Text;
import mtr.screen.WidgetBetterCheckbox;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The client-side configuration screen of the Joban Client Mod.<br>
 * Ported from JCM 2.3's {@code com.lx862.jcm.mod.gui.screen.ClientConfigScreen}.
 */
public class ClientConfigScreen extends TitledScreen implements GuiHelper {
	private static final ResourceLocation TEXTURE_BACKGROUND = new ResourceLocation("jsblock:textures/gui/background/bg.png");
	private static final ResourceLocation TEXTURE_STAR = new ResourceLocation("jsblock:textures/gui/background/stars.png");
	private static final ResourceLocation TEXTURE_TERRAIN = new ResourceLocation("jsblock:textures/gui/background/terrain.png");
	private static final int STAR_ROTATION_LENGTH = 260 * 1000;

	private final WidgetSet bottomRowWidget;
	private final WidgetBetterCheckbox ignoreVersionCheckButton;
	private final WidgetBetterCheckbox debugModeButton;
	private final WidgetBetterCheckbox disableScriptingButton;
	private final WidgetBetterCheckbox disableScriptingClassRestrictionButton;
	private final WidgetBetterCheckbox scriptDebugModeButton;
	private final WidgetBetterCheckbox scriptShowLogSourceButton;
	private final WidgetBetterCheckbox renderDisabledButton;
	private final WidgetBetterCheckbox scriptErrorNotificationButton;
	private final WidgetBetterCheckbox legacyConfigUiButton;
	private final Map<WidgetBetterCheckbox, Supplier<Boolean>> linkedConfig;
	private final Map<WidgetBetterCheckbox, Consumer<Boolean>> linkedSetter;

	private boolean discardConfig = false;

	public ClientConfigScreen() {
		super(Text.translatable("gui.jsblock.brand"), true);
		bottomRowWidget = new WidgetSet(20);
		linkedConfig = new HashMap<>();
		linkedSetter = new HashMap<>();

		this.ignoreVersionCheckButton = checkboxForConfig(ClientConfig::getVersionCheckDisabled, ClientConfig::setVersionCheckDisabled);
		this.debugModeButton = checkboxForConfig(ClientConfig::getDebugModeEnabled, ClientConfig::setDebugMode);
		this.disableScriptingButton = checkboxForConfig(ClientConfig::getSkipScriptParsing, ClientConfig::setSkipScriptParsing);
		this.disableScriptingClassRestrictionButton = checkboxForConfig(ClientConfig::getDisableScriptRestrictions, ClientConfig::setDisableScriptRestrictions);
		this.scriptDebugModeButton = checkboxForConfig(ClientConfig::getScriptDebugMode, ClientConfig::setScriptDebugMode);
		this.scriptShowLogSourceButton = checkboxForConfig(ClientConfig::getScriptShowLogSource, ClientConfig::setScriptShowLogSource);
		this.renderDisabledButton = checkboxForConfig(ClientConfig::getRenderDisabled, ClientConfig::setRenderDisabled);
		this.scriptErrorNotificationButton = checkboxForConfig(ClientConfig::isScriptErrorNotificationEnabled, ClientConfig::setScriptErrorNotification);
		this.legacyConfigUiButton = checkboxForConfig(ClientConfig::isLegacyConfigUi, ClientConfig::setLegacyConfigUi);
	}

	private WidgetBetterCheckbox checkboxForConfig(Supplier<Boolean> getter, Consumer<Boolean> setter) {
		final WidgetBetterCheckbox checkbox = new WidgetBetterCheckbox(0, 0, 20, 20, CommonComponents.EMPTY, bl -> {});
		linkedConfig.put(checkbox, getter);
		linkedSetter.put(checkbox, setter);
		return checkbox;
	}

	@Override
	public Component getScreenTitle() {
		return Text.translatable("gui.jsblock.brand");
	}

	@Override
	public Component getScreenSubtitle() {
		return Text.translatable("gui.jsblock.config.version", Joban.getVersion());
	}

	@Override
	protected void init2() {
		super.init2();
		bottomRowWidget.reset();

		final int contentWidth = (int) Math.min((width * 0.75), MAX_CONTENT_WIDTH);
		final int startX = (width - contentWidth) / 2;
		final int startY = TEXT_PADDING * 5;
		/* JCM 2.3 capped this list at 75% of the screen height. With the two YJCM-specific rows it
		   no longer fits, so the list takes whatever the title and the bottom button rows leave. */
		final int bottomEntryHeight = Math.max(40, Math.min(56, height / 10));
		final int listViewHeight = Math.max(120,
				height - startY - bottomEntryHeight - (BOTTOM_ROW_MARGIN * 2));

		final ListViewWidget listViewWidget = new ListViewWidget();
		addConfigEntries(listViewWidget);
		addBottomButtons(this.bottomRowWidget);
		addRenderableWidget(listViewWidget);
		addRenderableWidget(this.bottomRowWidget);
		listViewWidget.setXYSize(startX, startY, contentWidth, listViewHeight);
		bottomRowWidget.setXYSize(startX, startY + listViewHeight + BOTTOM_ROW_MARGIN, contentWidth, bottomEntryHeight);
	}

	private void setEntryStateFromClientConfig() {
		linkedConfig.forEach((checkbox, getter) -> checkbox.setChecked(getter.get()));
	}

	private void addConfigEntries(ListViewWidget listViewWidget) {
		setEntryStateFromClientConfig();

		linkedConfig.keySet().forEach(this::addWidget);

		// General
		listViewWidget.addCategory(Text.translatable("gui.jsblock.config.listview.category.general"));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.ignore_version_check"), new MappedWidget(ignoreVersionCheckButton));
		/* YJCM-specific: the old config screen exposed these two, and dropping them would make
		   a debug switch and the chat error report unreachable. Same list styling as the rest. */
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.disable_render"), new MappedWidget(renderDisabledButton));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.legacy_config_ui"), new MappedWidget(legacyConfigUiButton));

		// Debug
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.debug_mode"), new MappedWidget(debugModeButton));

		// Scripting
		listViewWidget.addCategory(Text.translatable("gui.jsblock.config.listview.category.scripting"));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.disable_scripting"), new MappedWidget(disableScriptingButton));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.script_error_notification"), new MappedWidget(scriptErrorNotificationButton));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.disable_scripting_class_restriction"), new MappedWidget(disableScriptingClassRestrictionButton));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.script_debug_mode"), new MappedWidget(scriptDebugModeButton));
		listViewWidget.add(Text.translatable("gui.jsblock.config.listview.title.script_show_log_source"), new MappedWidget(scriptShowLogSourceButton));
	}

	private void addBottomButtons(WidgetSet bottomRowWidget) {
		final Button latestLogButton = newButton(Text.translatable("gui.jsblock.config.latest_log"), btn -> openLatestLog());
		final Button crashLogButton = newButton(Text.translatable("gui.jsblock.config.crash_log"), btn -> openLatestCrashReport());
		final Button saveButton = newButton(Text.translatable("gui.jsblock.config.save"), btn -> onClose2());
		final Button discardButton = newButton(Text.translatable("gui.jsblock.config.discard"), btn -> {
			discardConfig = true;
			onClose2();
		});
		final Button resetButton = newButton(Text.translatable("gui.jsblock.config.reset"), btn -> {
			ClientConfig.loadDefaults();
			setEntryStateFromClientConfig();
		});

		addWidget(latestLogButton);
		addWidget(crashLogButton);
		addWidget(saveButton);
		addWidget(discardButton);
		addWidget(resetButton);

		bottomRowWidget.addWidget(latestLogButton);
		bottomRowWidget.addWidget(crashLogButton);
		bottomRowWidget.insertRow();
		bottomRowWidget.addWidget(saveButton);
		bottomRowWidget.addWidget(discardButton);
		bottomRowWidget.addWidget(resetButton);
	}

	private static Button newButton(Component text, Button.OnPress onPress) {
		return Button.builder(text, onPress).bounds(0, 0, 0, 20).build();
	}

	@Override
	public void drawBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
		final double terrainHeight = (width / 3.75);
		final double starSize = Math.max(width, height) * 4;
		final float starUVSize = (float) (starSize / 384F);
		final double translateY = height * (1 - animationProgress);

		GuiHelper.drawTexture(guiGraphics, TEXTURE_BACKGROUND, 0, 0, width, height);

		final PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();
		poseStack.translate(0, translateY * 0.2f, 0);
		poseStack.translate(width / 2.0, height / 2.0, 0);

		final float starRot = (System.currentTimeMillis() % STAR_ROTATION_LENGTH) / (float) STAR_ROTATION_LENGTH;
		poseStack.mulPose(Axis.ZP.rotationDegrees(starRot * 360));
		poseStack.translate(-width / 2.0, -height / 2.0, 0);
		GuiHelper.drawTexture(guiGraphics, TEXTURE_STAR, 0, 0, starSize, starSize, 0, 0, starUVSize, starUVSize);
		poseStack.popPose();

		GuiHelper.drawTexture(guiGraphics, TEXTURE_TERRAIN, 0, translateY + height - terrainHeight, width, terrainHeight);
	}

	@Override
	public void onClose2() {
		if (!closing) {
			if (!discardConfig) {
				// Apply all pending changes
				linkedSetter.forEach((checkbox, setter) -> setter.accept(checkbox.selected()));
				ClientConfig.writeConfig();
				com.jsblock.script.ScriptEngine.reset();
			} else {
				// Discard all pending changes
				ClientConfig.loadConfig();
				setEntryStateFromClientConfig();
			}
		}

		super.onClose2();
	}

	public static void openLatestLog() {
		final File latestLog = Paths.get(Minecraft.getInstance().gameDirectory.toString(), "logs", "latest.log").toFile();

		if (latestLog.exists()) {
			Util.getPlatform().openFile(latestLog);
		} else {
			showToast("gui.jsblock.config.nologfound");
		}
	}

	public static void openLatestCrashReport() {
		final File crashReportDir = Paths.get(Minecraft.getInstance().gameDirectory.toString(), "crash-reports").toFile();

		if (crashReportDir.exists()) {
			final File[] crashReports = crashReportDir.listFiles();

			if (crashReports != null && crashReports.length > 0) {
				final SimpleDateFormat crashReportFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

				Arrays.sort(crashReports, (o1, o2) -> {
					final String filename1 = o1.getName().replace("crash-", "").replace("-client", "").replace(".txt", "");
					final String filename2 = o2.getName().replace("crash-", "").replace("-client", "").replace(".txt", "");
					if (o1 == o2) return 0;

					try {
						final long ts1 = crashReportFormat.parse(filename1).getTime();
						final long ts2 = crashReportFormat.parse(filename2).getTime();
						return Long.compare(ts2, ts1);
					} catch (ParseException e) {
						Joban.LOGGER.warn("Cannot compare crash report file name {} <-> {}", filename1, filename2);
					}

					return 1;
				});

				Util.getPlatform().openFile(crashReports[0]);
				return;
			}
		}

		showToast("gui.jsblock.config.nocrashlogfound");
	}

	private static void showToast(String translationKey) {
		final SystemToast toast = SystemToast.multiline(Minecraft.getInstance(), SystemToast.SystemToastIds.TUTORIAL_HINT, Text.translatable("gui.jsblock.brand"), Text.translatable(translationKey));
		Minecraft.getInstance().getToasts().addToast(toast);
	}
}
