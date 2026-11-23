package com.jsblock.pids.component;

import com.google.gson.JsonObject;
import com.jsblock.pids.PIDSAlign;
import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSContext;
import com.jsblock.pids.PIDSGraphics;
import mtr.data.IGui;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws an arbitrary texture from a resource pack.
 *
 * <p>Ported from JCM 2.x's {@code CustomTextureComponent}. This is how a preset supplies
 * its own artwork (route logos, operator marks, decorative frames) without shipping a new
 * block.</p>
 *
 * <p>Options: {@code texture} (required), {@code tint} (hex, default opaque white),
 * {@code translucent} (default {@code true}).</p>
 */
public class CustomTextureComponent extends PIDSComponent {

	private final ResourceLocation texture;
	private final int tint;
	private final boolean translucent;

	private CustomTextureComponent(double x, double y, double width, double height,
								   ResourceLocation texture, int tint, boolean translucent) {
		super(x, y, width, height);
		this.texture = texture;
		this.tint = tint;
		this.translucent = translucent;
	}

	public static CustomTextureComponent parse(double x, double y, double width, double height, JsonObject json) {
		final String raw = optString(json, "texture", "");
		ResourceLocation texture = null;
		if (!raw.isEmpty()) {
			try {
				texture = new ResourceLocation(raw);
			} catch (Exception ignored) {
				// Leave null; canRender() then skips this component.
			}
		}
		return new CustomTextureComponent(
				x, y, width, height,
				texture,
				PIDSAlign.color(json, "tint", IGui.ARGB_WHITE),
				optBoolean(json, "translucent", true)
		);
	}

	@Override
	public boolean canRender(PIDSContext context) {
		return texture != null;
	}

	@Override
	public void render(PIDSContext context, PIDSGraphics graphics, float x, float y, float width, float height) {
		if (texture != null) {
			drawTexture(graphics, texture, x, y, width, height, tint, translucent);
		}
	}
}
