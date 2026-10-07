package com.jsblock.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsblock.Joban;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Class responsible for reading/saving the config value.<br>
 * For config screen, see {@link com.jsblock.screen.ConfigScreen} and {@link com.jsblock.screen.ConfigScreenBase}
 * @author LX86
 * @since 1.0.6
 */
public class ClientConfig {
    private static final String CONFIG_PATH = System.getProperty("user.dir") + "/config/" + "jsclient.json";
    private static boolean renderDisabled = false;
    private static boolean bypassServerVersionCheck = false;
    private static boolean debugMode = false;
    /**
     * Whether a PIDS script that throws should say so in chat.
     *
     * <p>On by default: a panel that stays black looks the same whether its script threw or
     * simply draws nothing, and the log is not where a player looks. JCM 2.x ties this to its
     * debug switch instead, which leaves exactly the player who needs it without it.</p>
     */
    private static boolean scriptErrorNotifications = true;
    /** Whether to draw the PIDS scripting debug overlay. Off by default; it is a dev tool. */
    private static boolean scriptDebugMode = false;
    /** Whether to let PIDS scripts reach arbitrary Java classes. See {@code ScriptClassShutter}. */
    private static boolean scriptRestrictionsDisabled = false;

    /**
     * This loads the config file and sets the variable internally
     */
    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_PATH))) {
            Joban.LOGGER.warn("[Joban Client] Config file not found, generating one...");
            try {
                writeConfig();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        Joban.LOGGER.info("[Joban Client] Reading Config...");
        try {
            final JsonObject jsonConfig = new JsonParser().parse(String.join("", Files.readAllLines(Paths.get(CONFIG_PATH)))).getAsJsonObject();

            if (jsonConfig.has("renderDisabled")) {
                renderDisabled = jsonConfig.get("renderDisabled").getAsBoolean();
            }

            if(jsonConfig.has("bypassVersionCheck")) {
                bypassServerVersionCheck = jsonConfig.get("bypassVersionCheck").getAsBoolean();
            }

            if(jsonConfig.has("debugMode")) {
                debugMode = jsonConfig.get("debugMode").getAsBoolean();
            }

            /* PIDS scripting switches. Each falls back to its default when absent, so an
               existing jsclient.json from an older build keeps working unchanged. */
            if(jsonConfig.has("scriptErrorNotifications")) {
                scriptErrorNotifications = jsonConfig.get("scriptErrorNotifications").getAsBoolean();
            }

            if(jsonConfig.has("scriptDebugMode")) {
                scriptDebugMode = jsonConfig.get("scriptDebugMode").getAsBoolean();
            }

            if(jsonConfig.has("scriptRestrictionsDisabled")) {
                scriptRestrictionsDisabled = jsonConfig.get("scriptRestrictionsDisabled").getAsBoolean();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                writeConfig();
            } catch (Exception er) {
                er.printStackTrace();
            }
        }
    }

    /**
     * This writes the current config
     */
    public static void writeConfig() {
        Joban.LOGGER.info("[Joban Client] Writing Config...");
        final JsonObject jsonConfig = new JsonObject();
        jsonConfig.addProperty("renderDisabled", renderDisabled);
        jsonConfig.addProperty("bypassVersionCheck", bypassServerVersionCheck);
        jsonConfig.addProperty("debugMode", debugMode);
        jsonConfig.addProperty("scriptErrorNotifications", scriptErrorNotifications);
        jsonConfig.addProperty("scriptDebugMode", scriptDebugMode);
        jsonConfig.addProperty("scriptRestrictionsDisabled", scriptRestrictionsDisabled);

        try {
            Files.write(Paths.get(CONFIG_PATH), Collections.singleton(new GsonBuilder().setPrettyPrinting().create().toJson(jsonConfig)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean getRenderDisabled() {
        return renderDisabled;
    }

    public static boolean getVersionCheckDisabled() {
        return bypassServerVersionCheck;
    }

    public static boolean getDebugModeEnabled() {
        return debugMode;
    }

    public static boolean setRenderDisabled(boolean disabled) {
        renderDisabled = disabled;
        return renderDisabled;
    }

    public static boolean setDebugMode(boolean enabled) {
        debugMode = enabled;
        return debugMode;
    }

    public static boolean setVersionCheckDisabled(boolean disabled) {
        bypassServerVersionCheck = disabled;
        return bypassServerVersionCheck;
    }

    /** @return whether a PIDS script that throws should be reported in chat. */
    public static boolean isScriptErrorNotificationEnabled() {
        return scriptErrorNotifications;
    }

    public static boolean setScriptErrorNotification(boolean enabled) {
        scriptErrorNotifications = enabled;
        return scriptErrorNotifications;
    }

    /** @return whether the PIDS scripting debug overlay is drawn. */
    public static boolean getScriptDebugMode() {
        return scriptDebugMode;
    }

    public static boolean setScriptDebugMode(boolean enabled) {
        scriptDebugMode = enabled;
        return scriptDebugMode;
    }

    /**
     * @return whether PIDS scripts may reach arbitrary Java classes. When {@code true} the
     * class shutter is off, exactly as JCM 2.x behaves once the player accepts the warning.
     */
    public static boolean getScriptRestrictionsDisabled() {
        return scriptRestrictionsDisabled;
    }

    public static boolean setScriptRestrictionsDisabled(boolean disabled) {
        scriptRestrictionsDisabled = disabled;
        return scriptRestrictionsDisabled;
    }
}
