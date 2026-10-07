package com.jsblock.screen;

import com.jsblock.Joban;
import com.jsblock.client.ClientConfig;
import com.jsblock.data.InlineComponentEntry;
import com.jsblock.data.ScreenAlignment;
import com.jsblock.data.ScreenRoot;
import com.jsblock.data.TextLabel;
import mtr.data.IGui;
import mtr.mappings.Text;
import mtr.mappings.UtilitiesClient;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The configuration screen for JCM.<br>
 * Background panorama and all the fancy stuff is in {@link com.jsblock.screen.ConfigScreenBase}
 * @author LX86
 * @since 1.1.4
 */
public class ConfigScreen extends ConfigScreenBase implements IGui {

	private boolean enableRendering;
	private boolean ignoreVerCheck;
	private boolean debugMode;
	private boolean scriptErrorNotifications;
	private boolean scriptDebugMode;
	private boolean scriptRestrictionsDisabled;
	private boolean initalized;

	private static final Component TITLE_TEXT = Text.translatable("gui.jsblock.brand");
	private static final Component VERSION_TEXT = Text.translatable("gui.jsblock.version", Joban.getVersion());

	public ConfigScreen() {
		super(
			new ScreenRoot(ScreenAlignment.HORZ_CENTER, 1.25F, 1),
			new TextLabel(TITLE_TEXT, 2, ScreenAlignment.HORZ_CENTER, TEXT_PADDING),
			new TextLabel(VERSION_TEXT, 1, ScreenAlignment.HORZ_CENTER, TEXT_PADDING)
		);
	}

	@Override
	protected void init() {
		if(!initalized) {
			initalized = true;
			enableRendering = ClientConfig.getRenderDisabled();
			ignoreVerCheck = ClientConfig.getVersionCheckDisabled();
			debugMode = ClientConfig.getDebugModeEnabled();

			registerConfigRowButton(Text.translatable("gui.jsblock.config.enable_render"), getBooleanButtonText(enableRendering), button -> {
				enableRendering = ClientConfig.setRenderDisabled(!enableRendering);
				button.setMessage(getBooleanButtonText(enableRendering));
			});

			registerConfigRowButton(Text.translatable("gui.jsblock.config.ignore_ver_check"), getBooleanButtonText(ignoreVerCheck), button -> {
				ignoreVerCheck = ClientConfig.setVersionCheckDisabled(!ignoreVerCheck);
				button.setMessage(getBooleanButtonText(ignoreVerCheck));
			});

			registerConfigRowButton(Text.translatable("gui.jsblock.config.debug_mode"), getBooleanButtonText(debugMode), button -> {
				debugMode = ClientConfig.setDebugMode(!debugMode);
				button.setMessage(getBooleanButtonText(debugMode));
			});

			scriptErrorNotifications = ClientConfig.isScriptErrorNotificationEnabled();
			scriptDebugMode = ClientConfig.getScriptDebugMode();
			scriptRestrictionsDisabled = ClientConfig.getScriptRestrictionsDisabled();

			registerConfigRowButton(Text.translatable("gui.jsblock.config.script_error_notifications"), getBooleanButtonText(scriptErrorNotifications), button -> {
				scriptErrorNotifications = ClientConfig.setScriptErrorNotification(!scriptErrorNotifications);
				button.setMessage(getBooleanButtonText(scriptErrorNotifications));
			});

			registerConfigRowButton(Text.translatable("gui.jsblock.config.script_debug_mode"), getBooleanButtonText(scriptDebugMode), button -> {
				scriptDebugMode = ClientConfig.setScriptDebugMode(!scriptDebugMode);
				button.setMessage(getBooleanButtonText(scriptDebugMode));
			});

			/* Turning the restrictions OFF is the direction that needs a warning, so that is
			   the only direction that asks. Turning them back on is always safe and silent. */
			registerConfigRowButton(Text.translatable("gui.jsblock.config.script_restrictions_disabled"), getBooleanButtonText(scriptRestrictionsDisabled), button -> {
				if (!scriptRestrictionsDisabled) {
					UtilitiesClient.setScreen(minecraft, new ScriptRestrictionWarningScreen(
							() -> {
								scriptRestrictionsDisabled = ClientConfig.setScriptRestrictionsDisabled(true);
								button.setMessage(getBooleanButtonText(scriptRestrictionsDisabled));
							},
							() -> button.setMessage(getBooleanButtonText(scriptRestrictionsDisabled))));
					return;
				}
				scriptRestrictionsDisabled = ClientConfig.setScriptRestrictionsDisabled(false);
				button.setMessage(getBooleanButtonText(scriptRestrictionsDisabled));
			});

			Button saveButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.config.save"), button1 -> {
				closeScreen(true);
			});

			Button discardButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.config.discard"), button1 -> {
				closeScreen(false);
			});

			Button resetButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.config.reset"), button1 -> {
				ClientConfig.setRenderDisabled(false);
				ClientConfig.setVersionCheckDisabled(false);
				ClientConfig.setDebugMode(false);
				ClientConfig.setScriptErrorNotification(true);
				ClientConfig.setScriptDebugMode(false);
				ClientConfig.setScriptRestrictionsDisabled(false);
				closeScreen(true);
			});

			Button latestLogButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.config.openlog"), button1 -> showLog());
			Button crashLogButton = UtilitiesClient.newButton(Text.translatable("gui.jsblock.config.opencrashlog"), button1 -> showCrashLog());

			InlineComponentEntry inlineRowConfig = new InlineComponentEntry(ScreenAlignment.HORZ_CENTER, ScreenAlignment.VERT_BOTTOM, saveButton, discardButton, resetButton);
			InlineComponentEntry inlineRowLogs = new InlineComponentEntry(ScreenAlignment.HORZ_CENTER, ScreenAlignment.VERT_BOTTOM, latestLogButton, crashLogButton);
			registerInlineRow(inlineRowLogs, inlineRowConfig);
		}
		super.init();
	}

	private void showLog() {
		File latestLog = Paths.get(minecraft.gameDirectory.toString(), "logs", "latest.log").toFile();
		if(latestLog.exists()) {
			Util.getPlatform().openFile(latestLog);
		}
	}

	private void showCrashLog() {
		Path logDir = Paths.get(minecraft.gameDirectory.toString(), "crash-reports");
		if(Files.exists(logDir)) {
			File[] crashLogList = logDir.toFile().listFiles();
			if(crashLogList != null && crashLogList.length > 0) {
				Arrays.sort(crashLogList, Comparator.comparingLong(File::lastModified));
				Util.getPlatform().openFile(crashLogList[0]);
			} else {
				/* Show up a toast notification if can't find any crash log */
				SystemToast errorToast = SystemToast.multiline(minecraft, SystemToast.SystemToastIds.TUTORIAL_HINT, Text.translatable("gui.jsblock.brand"), Text.translatable("gui.jsblock.config.nocrashlogfound"));
				minecraft.getToasts().addToast(errorToast);
			}
		}
	}

	@Override
	public void onSave() {
		/* Save the config */
		try {
			ClientConfig.writeConfig();
		} catch (Exception e) {
			/* Show up a toast notification if can't save the config */
			SystemToast errorToast = SystemToast.multiline(minecraft, SystemToast.SystemToastIds.TUTORIAL_HINT, Text.translatable("gui.jsblock.brand"), Text.translatable("gui.jsblock.config.savefailed"));
			minecraft.getToasts().addToast(errorToast);
		}
		super.onClose();
	}
}