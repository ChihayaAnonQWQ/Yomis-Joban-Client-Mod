package com.jsblock.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsblock.Joban;
import com.jsblock.data.PIDSPreset;
import com.jsblock.data.PIDSPresetEntry;
import mtr.mappings.Utilities;
import mtr.mappings.UtilitiesClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Class responsible for handling custom resources stuff.
 * @author LX86
 * @since 1.1.4
 */
public class JobanCustomResources {
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
        /* A pack's pixelation defaults belong to the packs that are loaded, so they are dropped
           with them -- otherwise removing a pack would leave its grid size behind. */
        com.jsblock.data.PackPixelation.reset();

        /* Every built-in preset comes from the mod's own joban_custom_resources.json now. Keeping
           hard-coded copies here meant a JSON entry with the same id was skipped by the
           containsKey guard below -- and those copies had no display name, so the picker listed
           them by id. */

        /* Read resource */
        readResource(manager, customResourcePath, jsonConfig -> {
            try {
                /* The pack-level pixelation block, when this pack has one. Read before the presets
                   so a preset that declares nothing of its own inherits it. */
                com.jsblock.data.PackPixelation.read(jsonConfig);

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

    /**
     * @return the built-in presets, in declaration order — the picker lists them before any pack's
     */
    public static List<PIDSPresetEntry> getBuiltInPresets() {
        final List<PIDSPresetEntry> presets = new ArrayList<>();

        for (Map.Entry<String, PIDSPreset> entry : PIDSPresets.entrySet()) {
            if (entry.getValue().builtin) {
                presets.add(new PIDSPresetEntry(entry.getKey(), entry.getValue()));
            }
        }

        return presets;
    }

    /**
     * @return the presets resource packs declared, sorted by id so the list does not reshuffle
     *         between reloads (a HashMap's order is not stable)
     */
    public static List<PIDSPresetEntry> getCustomPresets() {
        final List<PIDSPresetEntry> presets = new ArrayList<>();
        final List<String> ids = new ArrayList<>();

        for (Map.Entry<String, PIDSPreset> entry : PIDSPresets.entrySet()) {
            if (!entry.getValue().builtin) {
                ids.add(entry.getKey());
            }
        }

        Collections.sort(ids);

        for (String id : ids) {
            presets.add(new PIDSPresetEntry(id, PIDSPresets.get(id)));
        }

        return presets;
    }

    /** @return the entry for {@code id}, or {@code null} when no preset has that id */
    public static PIDSPresetEntry getPresetEntry(String id) {
        final PIDSPreset preset = id == null ? null : PIDSPresets.get(id);
        return preset == null ? null : new PIDSPresetEntry(id, preset);
    }

    /** @return all preset ids, in declaration order */
    public static List<String> getPresetIds() {
        return new ArrayList<>(PIDSPresets.keySet());
    }
}
