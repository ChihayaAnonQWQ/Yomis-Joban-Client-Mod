package com.jsblock.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsblock.Joban;
import com.jsblock.data.PIDSPreset;
import mtr.mappings.Utilities;
import mtr.mappings.UtilitiesClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * Class responsible for handling custom resources stuff.
 * @author LX86
 * @since 1.1.4
 */
public class JobanCustomResources {
    private final static PIDSPreset defaultPreset1 = new PIDSPreset(new ResourceLocation(Joban.MOD_ID, "textures/pids_screen/door_cls_apg.png"), true, true, false, new boolean[]{true, true, true, false},null, null, null);
    private final static PIDSPreset defaultPreset2 = new PIDSPreset(new ResourceLocation(Joban.MOD_ID, "textures/pids_screen/door_cls_psd.png"), true, true, false, new boolean[]{true, true, true, false},null, null, null);
    private final static PIDSPreset defaultPreset3 = new PIDSPreset(new ResourceLocation(Joban.MOD_ID, "textures/pids_screen/door_cls_train.png"), true, true, false, new boolean[]{true, true, true, false},null, null, null);
    public static final String CUSTOM_RESOURCES_ID = "joban_custom_resources";
    public static final String customResourcePath = Joban.MOD_ID + ":" + CUSTOM_RESOURCES_ID + ".json";
    public static HashMap<String, PIDSPreset> PIDSPresets = new HashMap<>();

    /**
     * Should be called when resource manager is being reloaded.
     */
    public static void reload(ResourceManager manager) {
        PIDSPresets.clear();
        /* Compiled PIDS programs hold a snapshot of the previous resource pack's scripts, so
           they must be dropped alongside the presets that reference them. */
        com.jsblock.script.ScriptEngine.reset();
        /* The per-block diagnostics say which path draws a panel; they have to be re-armed or
           a panel switched to a new preset would stay silent about it. */
        com.jsblock.render.RenderPIDSBase.forgetReportedPanels();

        /* Add default preset */
        PIDSPresets.put("door_cls_apg", defaultPreset1);
        PIDSPresets.put("door_cls_psd", defaultPreset2);
        PIDSPresets.put("door_cls_train", defaultPreset3);

        /* Read resource */
        readResource(manager, customResourcePath, jsonConfig -> {
            try {
                if(!jsonConfig.has("pids_images") || !jsonConfig.get("pids_images").isJsonArray()) {
                    Joban.LOGGER.warn("[JCM] Invalid joban_custom_resources.json!");
                    Joban.LOGGER.warn("[JCM] \"pids_images\" must be an array");
                    return;
                }

                jsonConfig.get("pids_images").getAsJsonArray().forEach(jsonElement -> {
                    String id = jsonElement.getAsJsonObject().get("id").getAsString();

                    if(PIDSPresets.containsKey(id)) {
                        Joban.LOGGER.warn("[Joban Client] PIDS Preset ID: " + id + " already added.");
                        return;
                    }

                    PIDSPreset preset = PIDSPreset.fromJson(jsonElement);
                    PIDSPresets.put(id, preset);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Joban.LOGGER.info("[Joban Client] Loaded PIDS Preset: " + String.join(", ", PIDSPresets.keySet()));
    }

    private static void readResource(ResourceManager manager, String path, Consumer<JsonObject> callback) {
        try {
            UtilitiesClient.getResources(manager, new ResourceLocation(path)).forEach(resource -> {
                try (final InputStream stream = Utilities.getInputStream(resource)) {
                    callback.accept(new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    Utilities.closeResource(resource);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception ignored) {
        }
    }
}
