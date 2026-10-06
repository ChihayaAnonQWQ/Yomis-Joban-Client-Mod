package com.jsblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSLayout;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;

public class PIDSPreset {
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
        return preset;
    }

    public Integer getCarColor(int car) {
        if(carLengthColorMap == null || !carLengthColorMap.containsKey(car - 1)) return null;
        return carLengthColorMap.get(car - 1);
    }
}
