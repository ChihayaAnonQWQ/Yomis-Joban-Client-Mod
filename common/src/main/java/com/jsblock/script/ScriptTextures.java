package com.jsblock.script;

import com.jsblock.Joban;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads the textures PIDS scripts reference.
 *
 * <h2>Why this is not just {@code new ResourceLocation(...)}</h2>
 * <p>Minecraft resolves a texture at {@code namespace:path} to
 * {@code assets/<namespace>/textures/<path>}. JCM 2.x does <b>not</b>: a script's identifier is
 * the path relative to {@code assets/<namespace>/}, exactly as written. The shipped
 * {@code HZYMTR CRT Pids1.0} pack depends on that difference — it stores</p>
 * <pre>
 *   assets/nanbin/pids/image/crt_pids_1.png          referenced as nanbin:pids/image/crt_pids_1.png
 *   assets/mtr/textures/texture/advertisement.png    referenced as mtr:textures/texture/advertisement.png
 * </pre>
 * <p>where only the second happens to sit under {@code textures/}. Feeding the raw identifier
 * to MTR's render layers would therefore look for
 * {@code assets/nanbin/textures/pids/image/crt_pids_1.png}, find nothing, and draw a black
 * panel. So each script texture is read from the {@code assets/} path and registered under the
 * identifier the script used, which keeps {@code MoreRenderLayers} working unchanged.</p>
 */
public final class ScriptTextures {

	/** Locations already registered, so the load happens once. */
	private static final Set<ResourceLocation> REGISTERED = new HashSet<>();
	/** Locations that could not be loaded, remembered so the log is not spammed per frame. */
	private static final Set<ResourceLocation> FAILED = new HashSet<>();

	private ScriptTextures() {
	}

	/**
	 * Makes sure the texture behind {@code location} is registered with the texture manager.
	 *
	 * @return the location to hand to the render layer — the same one, registered or not, so a
	 * missing texture still draws Minecraft's missing-texture placeholder rather than crashing
	 */
	public static ResourceLocation resolve(ResourceLocation location) {
		if (location == null || REGISTERED.contains(location) || FAILED.contains(location)) {
			return location;
		}
		/* Before the read: a script may only reach its own pack. A texture referenced through
		   Resources.id() never passed a builder that could check it, so the check lives here as
		   well as in the builder -- this is the call that actually opens the file. Rejected
		   locations are remembered as failed, so a per-frame draw reports once. See ScriptPaths. */
		try {
			ScriptPaths.checkLocation(location);
		} catch (ScriptPaths.RejectedPathException refused) {
			FAILED.add(location);
			ScriptPaths.report("Texture", refused);
			return location;
		}
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return location;
		}
		try {
			final ResourceManager resourceManager = minecraft.getResourceManager();
			final Resource resource = resourceManager == null ? null : resourceManager.getResource(location).orElse(null);
			if (resource == null) {
				FAILED.add(location);
				Joban.LOGGER.warn("[Joban Client] PIDS script texture \"{}\" not found at assets/{}/{}",
						location, location.getNamespace(), location.getPath());
				return location;
			}

			final NativeImage image;
			try (InputStream stream = resource.open()) {
				image = NativeImage.read(stream);
			}
			minecraft.getTextureManager().register(location, new UploadedTexture(image));
			REGISTERED.add(location);
		} catch (Exception e) {
			FAILED.add(location);
			Joban.LOGGER.warn("[Joban Client] PIDS script texture \"{}\" could not be loaded: {}",
					location, e.toString());
		}
		return location;
	}

	/** Drops the registration cache; called when the resource manager reloads. */
	public static void reset() {
		REGISTERED.clear();
		FAILED.clear();
	}

	/**
	 * Records that a location is already registered with the texture manager.
	 *
	 * <p>For {@link GraphicsTexture}, whose pixels come from a script rather than from the pack: the
	 * canvas registers its own texture under its own identifier, so {@link #resolve} must not go
	 * looking for a file that was never meant to exist and report it missing.</p>
	 */
	public static void markExternal(ResourceLocation location) {
		if (location != null) {
			REGISTERED.add(location);
			FAILED.remove(location);
		}
	}

	/** Forgets an externally registered location, so a released canvas is not remembered as live. */
	public static void forget(ResourceLocation location) {
		if (location != null) {
			REGISTERED.remove(location);
			FAILED.remove(location);
		}
	}

	/**
	 * Reads a pack image as an ARGB {@code BufferedImage}, for the scripting surface's
	 * {@code Resources.readBufferedImage} and for pasting a texture into a
	 * {@link GraphicsTexture}.
	 *
	 * <p>Goes through the same rules as every other script read — the path guard first, then the
	 * resource manager — and answers {@code null} with one warning rather than throwing, because a
	 * missing texture is a pack bug a script should survive: the canvas keeps working and the
	 * element that used it is simply absent, which is also what {@link #resolve} does for a missing
	 * texture.</p>
	 */
	public static java.awt.image.BufferedImage readImage(ResourceLocation location) {
		try {
			ScriptPaths.checkLocation(location);
		} catch (ScriptPaths.RejectedPathException refused) {
			ScriptPaths.report("Resources.readBufferedImage()", refused);
			return null;
		}
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getResourceManager() == null) {
			return null;
		}
		try (InputStream stream = minecraft.getResourceManager().getResource(location).orElseThrow().open()) {
			final java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(stream);
			if (image == null) {
				Joban.LOGGER.warn("[Joban Client] PIDS script image \"{}\" is not an image this JVM can read",
						location);
				return null;
			}
			return GraphicsTexture.createArgbBufferedImage(image);
		} catch (Exception e) {
			if (FAILED.add(location)) {
				Joban.LOGGER.warn("[Joban Client] PIDS script image \"{}\" could not be read: {}",
						location, e.toString());
			}
			return null;
		}
	}

	/**
	 * A texture whose pixels are uploaded straight from an image read out of the pack.
	 *
	 * <p>{@code AbstractTexture}'s normal {@code load} path cannot be used because it would go
	 * through Minecraft's {@code textures/} convention again, so the upload happens in the
	 * constructor and {@link #load} is left as a no-op.</p>
	 */
	private static final class UploadedTexture extends AbstractTexture {

		UploadedTexture(NativeImage image) {
			TextureUtil.prepareImage(getId(), image.getWidth(), image.getHeight());
			image.upload(0, 0, 0, false);
			image.close();
		}

		@Override
		public void load(ResourceManager resourceManager) {
			// Uploaded eagerly; nothing to do on a resource reload.
		}
	}
}
