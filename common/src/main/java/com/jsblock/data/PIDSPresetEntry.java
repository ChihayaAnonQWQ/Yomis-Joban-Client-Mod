package com.jsblock.data;

import net.minecraft.resources.ResourceLocation;

/**
 * A named {@link PIDSPreset}, as listed in the PIDS preset picker.
 *
 * <p>The picker is JCM 2.3's, and it works in terms of an entry rather than the preset itself: the
 * preset carries the rendering data, the entry carries the id the block stores and the display name
 * the list shows. Kept as a separate type for that reason, and because it is the seam the picker's
 * preview icon and its "allowed on this PIDS type" filter both go through.</p>
 */
public class PIDSPresetEntry {
	public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
	/** JCM 2.x's preview for a preset that ships no background image. */
	private static final ResourceLocation DEFAULT_THUMBNAIL = new ResourceLocation("jsblock:textures/gui/pids_preview_js.png");

	private final String id;
	private final PIDSPreset preset;

	public PIDSPresetEntry(String id, PIDSPreset preset) {
		this.id = id;
		this.preset = preset;
	}

	public String getId() {
		return id;
	}

	/** Display name of the preset, as declared by {@code name} in the JSON (defaults to the id). */
	public String getName() {
		return preset.name == null || preset.name.isEmpty() ? id : preset.name;
	}

	public PIDSPreset getPreset() {
		return preset;
	}

	/**
	 * @return the pack's {@code thumbnail} when it declares one, otherwise the full background, and
	 *         when a preset declares neither — a script preset needs no background — the generic
	 *         script preview. The picker draws this straight into the texture manager, and a null
	 *         location there is not a blank tile: it is a crash inside
	 *         {@code RenderSystem.setShaderTexture}, so this must never answer null.
	 */
	public ResourceLocation getThumbnail() {
		if (preset.thumbnail != null) {
			return preset.thumbnail;
		}
		if (preset.image != null) {
			return preset.image;
		}
		return DEFAULT_THUMBNAIL;
	}

	public boolean hasThumbnail() {
		return true;
	}

	public int getTextColor() {
		return preset.color == null ? DEFAULT_TEXT_COLOR : (0xFF000000 | preset.color);
	}

	public boolean isRowHidden(int row) {
		return preset.visibility != null && row >= 0 && row < preset.visibility.length && preset.visibility[row];
	}

	/** Whether this preset may be used by the given PIDS type. */
	public boolean typeAllowed(String pidsType) {
		return preset.allowsType(pidsType);
	}

	public boolean isBuiltin() {
		return preset.builtin;
	}

	/** Whether the preset draws itself through a script rather than the built-in row renderer. */
	public boolean isScriptPreset() {
		return preset.isScripted();
	}
}
