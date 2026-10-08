package com.jsblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSLayout;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PIDSPreset {
    /** The key this preset is registered under in {@code JobanCustomResources.PIDSPresets}. */
    public String id;
    public ResourceLocation image;
    public Integer color;
    public String font;
    private Int2IntArrayMap carLengthColorMap;
    public boolean[] visibility;
    public boolean customTextPushArrival;
    public boolean showWeather;
    public boolean showClock;

    /**
     * Optional component-based layout.
     *
     * <p>When a preset declares a {@code components} array, this holds the parsed layout and
     * renderers draw it instead of the hard-coded element positions. When {@code null}, the
     * preset behaves exactly as it did before layouts existed, so old resource packs keep
     * working unchanged.</p>
     *
     * @see PIDSLayout
     */
    public PIDSLayout layout;

    /**
     * Human-readable name shown in the PIDS config screen.
     *
     * <p>JCM 2.x field. Falls back to the preset id when absent, matching how JCM 2.x's
     * {@code PIDSPresetBase} treats a null name.</p>
     */
    public String name;

    /** Whether the preset ships with the mod rather than coming from a resource pack. JCM 2.x field. */
    public boolean builtin;

    /**
     * Pixel scale this preset asks for, or 1 for none.
     *
     * <p>A resource pack declares it in its preset entry as {@code "pixelScale": 2}, which is the
     * pack's way of saying it was drawn for a screen with visible pixels — the whole panel is then
     * rendered small and magnified, so its text and icons land on one coarse grid. A player can
     * override it per preset in the client config's {@code pixelScaleByPreset}, including setting it
     * back to 1.</p>
     *
     * @see com.jsblock.client.ClientConfig#effectivePixelScale
     */
    public int pixelScale = 1;

    /**
     * Script files that draw this preset, in load order.
     *
     * <p>JCM 2.x field: a JCM 2.x preset is a JavaScript file rather than a list of
     * components. An empty list means the preset is not scripted, and the JSON-component
     * ({@link #layout}) or built-in renderer path applies instead.</p>
     */
    public List<String> scriptFiles = Collections.emptyList();

    /**
     * JavaScript written directly in the preset entry, run before {@link #scriptFiles}.
     *
     * <p>JCM 2.x's {@code scriptTexts}. A pack that keeps a few lines of setup inline rather than
     * in a file needs it, and a preset carrying only {@code scriptTexts} is scripted just as much
     * as one carrying files -- which is why {@link #isScripted()} counts both.</p>
     */
    public List<String> scriptTexts = Collections.emptyList();

    /**
     * Arbitrary JSON the preset wants its scripts to see, exposed to them as {@code SCRIPT_INPUT}.
     *
     * <p>JCM 2.x's {@code scriptInput}: one script can then serve several boards by being told what
     * to announce, which station to name, and so on.</p>
     */
    public com.google.gson.JsonElement scriptInput = null;


    /**
     * PIDS block types this preset refuses to run on, by type name.
     *
     * <p>JCM 2.x field. JCM 2.x also keeps a global per-type blacklist in the preset name
     * space; here the list is consulted through {@link #allowsType(String)}.</p>
     */
    public List<String> blacklist = Collections.emptyList();

    public PIDSPreset(ResourceLocation image, boolean showWeather, boolean showClock, boolean customTextPushArrival, boolean[] visibility, Integer color, String font, Int2IntArrayMap carLengthColorMap) {
        this.image = image;
        this.showWeather = showWeather;
        this.showClock = showClock;
        this.visibility = visibility;
        this.color = color;
        this.font = font;
        this.customTextPushArrival = customTextPushArrival;
        this.carLengthColorMap = carLengthColorMap;
    }

    /** @return {@code true} when this preset is drawn by JavaScript rather than by JSON. */
    public boolean isScripted() {
        return (scriptFiles != null && !scriptFiles.isEmpty()) || (scriptTexts != null && !scriptTexts.isEmpty());
    }

    /** @return {@code true} when the preset may be used on the given PIDS type. */
    public boolean allowsType(String pidsType) {
        return blacklist == null || pidsType == null || !blacklist.contains(pidsType);
    }

    /** @return the display name, falling back to {@link #id} exactly as JCM 2.x does. */
    public String displayName() {
        return name == null || name.isEmpty() ? id : name;
    }

    public static PIDSPreset fromJson(JsonElement element) {
        boolean[] hideRowArray = new boolean[4];
        boolean showWeather = element.getAsJsonObject().has("showWeather") ? element.getAsJsonObject().get("showWeather").getAsBoolean() : true;
        boolean showClock = element.getAsJsonObject().has("showClock") ? element.getAsJsonObject().get("showClock").getAsBoolean() : true;
        boolean customTextPushArrival = element.getAsJsonObject().has("customTextPushArrival") && element.getAsJsonObject().get("customTextPushArrival").getAsBoolean();
        /* The preset key has always been "fonts" (plural) while the field and every renderer
           call it `font`; accept both spellings so neither can silently lose the font. */
        final JsonObject presetObject = element.getAsJsonObject();
        String fonts = null;
        if (presetObject.has("fonts")) {
            fonts = presetObject.get("fonts").getAsString();
        } else if (presetObject.has("font")) {
            fonts = presetObject.get("font").getAsString();
        }
        String hexColor = element.getAsJsonObject().has("color") ? element.getAsJsonObject().get("color").getAsString() : null;
        JsonArray carLengthColor = element.getAsJsonObject().has("carLengthColor") ? element.getAsJsonObject().get("carLengthColor").getAsJsonArray() : null;
        JsonArray hiddenRowList = element.getAsJsonObject().has("hideRow") ? element.getAsJsonObject().get("hideRow").getAsJsonArray() : null;
        /* "background" is optional: a layout preset may draw only components, and a missing
           field used to abort the whole pids_images list because fromJson threw. */
        final ResourceLocation background = element.getAsJsonObject().has("background")
                ? new ResourceLocation(element.getAsJsonObject().get("background").getAsString())
                : null;

        Int2IntArrayMap carLengthColorMap = null;

        Integer color = null;
        if(hexColor != null) {
            color = Integer.parseInt(hexColor, 16);
        }

        if(hiddenRowList != null) {
            for (int i = 0; i < Math.min(hiddenRowList.size(), hideRowArray.length); i++) {
                hideRowArray[i] = hiddenRowList.get(i).getAsBoolean();
            }
        } else {
            hideRowArray = null;
        }

        if(carLengthColor != null) {
            carLengthColorMap = new Int2IntArrayMap();
            for (int i = 0; i < carLengthColor.size(); i++) {
                JsonElement colorElement = carLengthColor.get(i);
                if(!colorElement.isJsonNull()) {
                    carLengthColorMap.put(i, Integer.parseInt(colorElement.getAsString(), 16));
                }
            }
        }
        final PIDSPreset preset = new PIDSPreset(background, showWeather, showClock, customTextPushArrival, hideRowArray, color, fonts, carLengthColorMap);
        /* Optional component-based layout. A preset without a "components" array keeps
           layout == null and is drawn by the built-in renderers as before. */
        if (element.isJsonObject()) {
            preset.layout = PIDSLayout.fromJson(element.getAsJsonObject());
        }

        /* JCM 2.x preset metadata. All of it is optional so JCM 1.x presets keep parsing. */
        preset.id = presetObject.has("id") ? presetObject.get("id").getAsString() : null;
        if (presetObject.has("name")) {
            preset.name = presetObject.get("name").getAsString();
        }
        preset.builtin = presetObject.has("builtin") && presetObject.get("builtin").getAsBoolean();
        preset.scriptFiles = readStringList(presetObject, "scriptFiles");
        preset.scriptTexts = readStringList(presetObject, "scriptTexts");
        if (presetObject.has("scriptInput")) {
        	preset.scriptInput = presetObject.get("scriptInput");
        }
        preset.blacklist = readStringList(presetObject, "blacklist");

        /* Whole-screen pixelation, declared by the pack. Read here rather than from the script
           because it is a property of the artwork, not of any one frame: where a pack drew for a
           dot-matrix screen it says so once in its preset entry and every panel using it follows,
           with no script API added and nothing to break on older builds that never heard of it. */
        if (presetObject.has("pixelScale")) {
            try {
                preset.pixelScale = presetObject.get("pixelScale").getAsInt();
            } catch (Exception e) {
                com.jsblock.Joban.LOGGER.warn("[Joban Client] Preset " + preset.id
                        + " has a pixelScale that is not a whole number; ignoring it.");
            }
        }

        return preset;
    }

    /**
     * Reads a JSON array of strings, tolerating a single string and skipping nulls.
     *
     * @return an immutable list, never {@code null}
     */
    private static List<String> readStringList(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return Collections.emptyList();
        }
        final JsonElement element = json.get(key);
        if (element.isJsonPrimitive()) {
            return Collections.singletonList(element.getAsString());
        }
        if (!element.isJsonArray()) {
            return Collections.emptyList();
        }
        final List<String> values = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry != null && !entry.isJsonNull()) {
                values.add(entry.getAsString());
            }
        }
        return Collections.unmodifiableList(values);
    }

    public Integer getCarColor(int car) {
        if(carLengthColorMap == null || !carLengthColorMap.containsKey(car - 1)) return null;
        return carLengthColorMap.get(car - 1);
    }
}
