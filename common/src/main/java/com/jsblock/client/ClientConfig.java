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
     * Pixel scale per preset id, for the whole-screen pixelation feature.
     *
     * <p>Keyed by preset id rather than by block: a preset is what decides how small its text and
     * how thin its lines are, so it is also what decides how coarse a grid they survive. Two panels
     * on one platform showing different presets can therefore pixelate differently, which is the
     * point — a pack drawn for a dot-matrix screen wants it, one drawn with 0.55-scale station
     * names does not.</p>
     *
     * <p>Absent means 1, which means draw straight to the world exactly as before. Keeping the
     * default in the config rather than in the renderer is what makes this a no-op for every
     * existing preset, and it is why the feature needs nothing from a resource pack.</p>
     */
    private static final java.util.Map<String, Integer> pixelScaleByPreset = new java.util.LinkedHashMap<>();

    /** The largest scale accepted, so a hand-edited config cannot ask for a one-pixel canvas. */
    public static final int MAX_PIXEL_SCALE = 8;

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

            /* Pixelation is stored as { "preset id": scale }. A missing object, a missing id or a
               value out of range all just mean "draw straight to the world" -- the feature has to
               be impossible to notice for anyone who has not asked for it. */
            pixelScaleByPreset.clear();
            if (jsonConfig.has("pixelScaleByPreset") && jsonConfig.get("pixelScaleByPreset").isJsonObject()) {
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : jsonConfig.getAsJsonObject("pixelScaleByPreset").entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    try {
                        final int scale = entry.getValue().getAsInt();
                        /* 1 is kept, not dropped: a pack may declare pixelation and a player may
                           want it off, and "off" has to be something they can say. */
                        if (scale >= 1) {
                            pixelScaleByPreset.put(entry.getKey(), Math.min(scale, MAX_PIXEL_SCALE));
                        }
                    } catch (Exception ignored) {
                        Joban.LOGGER.warn("[Joban Client] Ignoring pixel scale for preset " + entry.getKey() + ": not a number.");
                    }
                }
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

        if (!pixelScaleByPreset.isEmpty()) {
            final JsonObject pixelScales = new JsonObject();
            for (java.util.Map.Entry<String, Integer> entry : pixelScaleByPreset.entrySet()) {
                pixelScales.addProperty(entry.getKey(), entry.getValue());
            }
            jsonConfig.add("pixelScaleByPreset", pixelScales);
        }

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

    /**
     * @param presetId the preset's id, as registered in {@code JobanCustomResources.PIDSPresets}.
     * @return the pixel scale for that preset; 1 when unset, which means no pixelation.
     */
    public static int getPixelScale(String presetId) {
        if (presetId == null) {
            return 1;
        }
        return pixelScaleByPreset.getOrDefault(presetId, 1);
    }

    /**
     * The scale a preset should actually be drawn at: the player's entry for it if there is one,
     * otherwise whatever the resource pack declared.
     *
     * <p>The player wins, including when their entry says 1: a pack may ask for pixelation and a
     * player may disagree, and the config is where they say so. Both default to off, which is what
     * keeps this feature invisible to anyone who has not asked for it.</p>
     *
     * @param presetId the preset's id, possibly {@code null}
     * @param packScale the preset's own declaration; 1 when it made none
     */
    public static int effectivePixelScale(String presetId, int packScale) {
        if (presetId != null && pixelScaleByPreset.containsKey(presetId)) {
            return pixelScaleByPreset.get(presetId);
        }
        if (packScale <= 1) {
            return 1;
        }
        return Math.min(packScale, MAX_PIXEL_SCALE);
    }

    /**
     * Sets (or clears, when {@code scale <= 1}) a preset's pixel scale and writes the config.
     *
     * @return the scale actually stored.
     */
    public static int setPixelScale(String presetId, int scale) {
        if (presetId == null || presetId.isEmpty()) {
            return 1;
        }
        if (scale <= 1) {
            pixelScaleByPreset.put(presetId, 1);
        } else {
            pixelScaleByPreset.put(presetId, Math.min(scale, MAX_PIXEL_SCALE));
        }
        writeConfig();
        return getPixelScale(presetId);
    }

    /** A read-only view of the whole map, for reporting which presets are pixelated. */
    public static java.util.Map<String, Integer> getPixelScaleMap() {
        return java.util.Collections.unmodifiableMap(pixelScaleByPreset);
    }
}
