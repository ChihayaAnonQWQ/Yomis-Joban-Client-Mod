package com.jsblock.script;

import com.jsblock.Joban;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the AWT fonts a PIDS script reaches for.
 *
 * <p>The one entry point is {@link ScriptEngine.Resources#getSystemFont(String)}, which is JCM 2.x's
 * method of that name. Its name is misleading in the reference implementation and this is the place
 * that matters: despite "system", the two names it knows are <b>MTR's own bundled fonts</b>, read
 * out of the mod's resource pack with {@code Font.createFont} — javap of
 * {@code com.lx862.mtrscripting.util.ScriptResourceUtil.<clinit>} shows the three locations it
 * builds, and {@code readFont} shows {@code Font.createFont(0, readStream(id))}. Everything else
 * falls back to {@code new Font(name, PLAIN, 1)} — a name lookup in the JVM's own font registry,
 * not a file.</p>
 *
 * <h2>Why a font read out of the pack is not a "system font"</h2>
 * <p>A font that arrives through {@code Font.createFont} is <em>not</em> registered with the
 * {@code GraphicsEnvironment}: it has no family name anything else can look up, and it carries one
 * face. That is why a preset must scale it with {@code deriveFont} rather than construct a new
 * {@code Font} around its family — the pack's own header says so, and it is the trap that made the
 * first version of HKR's board draw nothing.</p>
 *
 * <p>Read through the same path rules as every other script read, though a script cannot choose
 * these locations: the two names are fixed strings, so there is nothing to escape with. The guard
 * is applied anyway, because "this caller happens to be safe today" is how a path check rots.</p>
 *
 * <h2>Failing</h2>
 * <p>A font that cannot be read answers {@code null} with one warning, never a throw. JCM 2.x
 * behaves the same way and the packs are written for it: HKR's {@code sysFont()} wraps the call in
 * a {@code try} and falls back to {@code new Font("SansSerif", PLAIN, 12)}. A panel drawn in the
 * wrong typeface is a cosmetic problem; a preset that cannot load is a black board.</p>
 */
public final class ScriptFonts {

	/** Locations already reported as unreadable, so a per-frame call does not flood the log. */
	private static final Set<ResourceLocation> FAILED = ConcurrentHashMap.newKeySet();

	/**
	 * Where the headless check gets its bytes.
	 *
	 * <p>With no client there is no resource manager, so the only way the check can exercise this
	 * class — and with it {@code Font.createFont} and the {@code deriveFont} contract the packs
	 * depend on — is to be handed the reader itself. Exactly the shape {@code ScriptApiCheck}
	 * already uses for {@code include()}: the production path is the game's resource manager and
	 * this is never consulted while one exists. Package-private because the checks live in this
	 * package.</p>
	 */
	static volatile java.util.function.Function<ResourceLocation, InputStream> headlessSource;

	private ScriptFonts() {
	}

	/**
	 * Reads a font file out of the installed packs.
	 *
	 * @return the font, or {@code null} when it is not there or is not a font this JVM can read
	 */
	public static Font read(ResourceLocation location) {
		if (location == null || FAILED.contains(location)) {
			return null;
		}
		try {
			ScriptPaths.checkLocation(location);
		} catch (ScriptPaths.RejectedPathException refused) {
			FAILED.add(location);
			ScriptPaths.report("Resources.getSystemFont()", refused);
			return null;
		}
		try (InputStream stream = open(location)) {
			if (stream == null) {
				throw new IOException("not present in any loaded resource pack");
			}
			return Font.createFont(Font.TRUETYPE_FONT, stream);
		} catch (FontFormatException | IOException | RuntimeException e) {
			if (FAILED.add(location)) {
				Joban.LOGGER.warn("[Joban Client] PIDS script font \"{}\" could not be read ({});"
						+ " a script asking for it falls back to a JVM font. {}", location,
						e.getClass().getSimpleName(), e.getMessage());
			}
			return null;
		}
	}

	/**
	 * Opens the font file, from the game's resource manager when there is one.
	 *
	 * <p>{@code createFont} needs the whole stream and takes a {@code Throwable} path of its own for
	 * a malformed file, so this hands back an open stream and lets {@link #read} close it.</p>
	 *
	 * <p>{@code mtr:font/noto-sans-semibold.ttf} is MTR's own asset and our MTR 3.6.3 ships it —
	 * verified by listing {@code libs/mtr-common.jar}: {@code assets/mtr/font/noto-sans-semibold.ttf},
	 * 515204 bytes. {@code mtr:font/noto-serif-cjk-tc-semibold.ttf} (28120132 bytes) is there too,
	 * for the "Noto Serif" name; {@code mtr:font/noto-sans-cjk-tc-medium.otf}, which the reference
	 * jar's dead CJK branch would want, is <b>not</b> — see
	 * {@link ScriptEngine.Resources#getSystemFont(String)}.</p>
	 */
	private static InputStream open(ResourceLocation location) throws IOException {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getResourceManager() != null) {
			return minecraft.getResourceManager().getResource(location).orElseThrow().open();
		}
		final java.util.function.Function<ResourceLocation, InputStream> source = headlessSource;
		if (source != null) {
			return source.apply(location);
		}
		return null;
	}

	/** Forgets which fonts were reported unreadable; called when the resource manager reloads. */
	public static void reset() {
		FAILED.clear();
	}
}
