package com.jsblock.script;

import com.jsblock.Joban;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.FilePackResources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a script out of the packs themselves, for the names Minecraft's resource manager cannot
 * hold.
 *
 * <h2>The problem this solves</h2>
 * <p>A {@code ResourceLocation} only accepts {@code [a-z0-9/._-]}. The constructor throws for
 * anything else, and Minecraft's pack loaders lower-case the first letter of every path component
 * while they index a pack — so a file stored as {@code scripts/Digital_Rail.js} is indexed as
 * {@code scripts/igital_Rail.js}, which is not its name and resolves to nothing.</p>
 *
 * <p>That is not hypothetical. {@code met transit} — the largest v2-era PIDS pack in the library
 * this port was tested against — ships {@code assets/jsblock/scripts/Digital_Rail.js} and
 * {@code Cyberpunk_Transit.js}, byte-for-byte with those capitals, and lists both in its
 * {@code scriptFiles}. Through the resource manager they are unreachable: there is no spelling of
 * the name that both the location rules accept and the index contains. Roughly a third of that
 * pack's presets therefore never compiled.</p>
 *
 * <h2>What it does instead</h2>
 * <p>It asks the packs directly. {@link UtilitiesClient#getResourcePackDirectory(Minecraft)} gives
 * the pack folder, {@code Minecraft.getResourcePackRepository().openAllSelected()} gives the pack
 * objects in load order, and each one is asked for the exact asset path the script named. A
 * directory pack is walked; a zip pack is opened and its entries matched. Neither path builds a
 * {@code ResourceLocation}, so neither is subject to the spelling rule.</p>
 *
 * <p>Only reached after the resource manager has already failed, so an ordinary pack — which is
 * every other pack in the library — never pays for this.</p>
 *
 * <h2>Why reflection, and what happens if it breaks</h2>
 * <p>A pack's root is not public API: {@code PathPackResources.root} and
 * {@code FilePackResources.file} are private fields with no accessors. Reflection is used to read
 * exactly those two and nothing else, on classes whose job is to hold a path, and a failure is
 * treated as "this pack cannot be searched" rather than as an error — the field is looked up
 * through the instance's own class and then its superclasses, so a Forge or Fabric reimplementation
 * with the same field name still works, and one without it is simply skipped. If every pack is
 * skipped the answer is {@code null}, which is what the caller would have got before.</p>
 */
final class ScriptPackFiles {

	/** Reported once, because the fallback runs whenever a preset fails to resolve a script. */
	private static final AtomicBoolean REPORTED = new AtomicBoolean();

	private ScriptPackFiles() {
	}

	/**
	 * Reads {@code assets/<namespace>/<path>} out of a loaded pack, tolerating the spelling.
	 *
	 * @param namespace the reference's namespace
	 * @param path      the reference's path, e.g. {@code scripts/Digital_Rail.js}
	 * @return the script's text, or {@code null} when no pack has it
	 */
	static String read(String namespace, String path) {
		return read(namespace, path, null);
	}

	/**
	 * As {@link #read(String, String)}, but reports the asset path that answered.
	 *
	 * <p>A pack can spell the path differently from the reference — that is the whole point of the
	 * fallback — and a stack trace from inside the script is much easier to act on when the name in
	 * it is the one on disk. When that differs from what was asked for, the caller is told through
	 * {@code resolvedTo}, which is where a script's own name for itself comes from.</p>
	 *
	 * @param resolvedTo filled with the winning asset path when one was found; may be {@code null}
	 */
	static String read(String namespace, String path, java.util.function.Consumer<String> resolvedTo) {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || namespace == null || path == null) {
			return null;
		}
		final String[] parts = path.split("/");
		final String[] assetPath = new String[parts.length + 2];
		assetPath[0] = "assets";
		assetPath[1] = namespace;
		System.arraycopy(parts, 0, assetPath, 2, parts.length);

		/* Utterly defensive: this is a fallback for a broken pack, and a pack whose loading threw
		   must not take a renderer with it. */
		List<PackResources> packs = null;
		try {
			if (minecraft.getResourcePackRepository() != null) {
				packs = minecraft.getResourcePackRepository().openAllSelected();
			}
		} catch (Throwable e) {
			report(e);
			return null;
		}
		if (packs == null) {
			return null;
		}
		try {
			for (PackResources pack : packs) {
				final String source = readFromPack(pack, assetPath);
				if (source != null && !source.trim().isEmpty()) {
					Joban.LOGGER.info("[Joban Client] PIDS script {}:{} was read from the pack \"{}\""
							+ " directly -- Minecraft's resource manager cannot index a name with"
							+ " capitals in it.", namespace, path, safeName(pack));
					if (resolvedTo != null) {
						resolvedTo.accept(String.join("/", assetPath));
					}
					return source;
				}
			}
		} catch (Throwable e) {
			report(e);
		} finally {
			close(packs);
		}
		return null;
	}

	private static String readFromPack(PackResources pack, String[] exact) {
		if (pack instanceof PathPackResources) {
			return readFromDirectory(pack, exact);
		}
		if (pack instanceof FilePackResources) {
			return readFromZip(pack, exact);
		}
		return null;
	}

	/**
	 * Walks a directory pack: the exact name first, then a case-insensitive match at each level.
	 *
	 * <p>The exact attempt is one {@code isRegularFile} call and is what answers for a pack that
	 * spelled the path without capitals. The loose attempt is a directory listing per segment,
	 * which is only reached for the packs that need it.</p>
	 */
	private static String readFromDirectory(PackResources pack, String[] parts) {
		final Object root = field(pack.getClass(), "root");
		if (!(root instanceof Path)) {
			return null;
		}
		return readFromDirectoryRoot((Path) root, parts);
	}

	/**
	 * The directory walk itself, separated from the reflection so it can be checked.
	 *
	 * <p>Package-private for the headless check, which builds a tree shaped like a pack and calls
	 * this directly: the reflection above and the pack enumeration beside it cannot run without a
	 * client, but the rule that decides which file to read can, and it is the part that has to be
	 * right. On a case-insensitive filesystem it is also the only part that carries the real
	 * spelling forward — a {@code Path} built from the requested name reports the requested name
	 * there, so the answer has to come from the listing.</p>
	 */
	static String readFromDirectoryRoot(Path base, String[] parts) {
		Path exact = base;
		for (String part : parts) {
			exact = exact.resolve(part);
		}
		if (Files.isRegularFile(exact)) {
			return readAll(exact);
		}
		return readFromDirectoryFolded(base, parts);
	}

	private static String readFromDirectoryFolded(Path base, String[] parts) {
		Path current = base;
		for (int i = 0; i < parts.length; i++) {
			final String part = parts[i];
			final boolean last = i == parts.length - 1;
			/* Keyed by the real on-disk name, so a candidate carries the spelling to read it by
			   rather than the spelling it was asked for -- the two differ exactly here. */
			final Map<String, Path> candidates = new LinkedHashMap<>();
			try (java.util.stream.Stream<Path> children = Files.list(current)) {
				children.filter(child -> child.getFileName().toString().equalsIgnoreCase(part))
						.filter(child -> last ? Files.isRegularFile(child) : Files.isDirectory(child))
						.forEach(child -> candidates.putIfAbsent(child.getFileName().toString(), child));
			} catch (Exception e) {
				return null;
			}
			if (candidates.isEmpty()) {
				return null;
			}
			final List<String> names = new ArrayList<>(candidates.keySet());
			names.sort(Comparator.comparing((String name) -> !name.equals(part)).thenComparing(Comparator.naturalOrder()));
			final Path chosen = candidates.get(names.get(0));
			if (last) {
				return readAll(chosen);
			}
			current = chosen;
		}
		return null;
	}

	/**
	 * Opens a zip pack and matches its entries.
	 *
	 * <p>No directory walk is attempted: a zip's entry names are lower-cased by the pack loader on
	 * the way into the index, but the archive itself keeps what was written, so the entries are the
	 * only place the real name survives — and there is no way to list one without scanning, which
	 * is bounded by the archive's size and happens once per script that the resource manager could
	 * not resolve.</p>
	 */
	private static String readFromZip(PackResources pack, String[] parts) {
		final Object file = field(pack.getClass(), "file");
		if (!(file instanceof File)) {
			return null;
		}
		final String wanted = String.join("/", parts);
		final String wantedFold = wanted.toLowerCase(Locale.ROOT);
		try (ZipFile zip = new ZipFile((File) file)) {
			/* An exact entry first, so an ordinary pack costs one hash lookup. */
			final ZipEntry exact = zip.getEntry(wanted);
			if (exact != null) {
				try (InputStream stream = zip.getInputStream(exact)) {
					return readAll(stream);
				}
			}
			ZipEntry best = null;
			final java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				final String name = entry.getName();
				if (!name.equalsIgnoreCase(wanted) && !name.toLowerCase(Locale.ROOT).equals(wantedFold)) {
					continue;
				}
				/* Shortest name wins, then alphabetical: deterministic when an archive holds two
				   spellings of the same path. */
				if (best == null || name.length() < best.getName().length()
						|| (name.length() == best.getName().length() && name.compareTo(best.getName()) < 0)) {
					best = entry;
				}
			}
			if (best == null) {
				return null;
			}
			try (InputStream stream = zip.getInputStream(best)) {
				return readAll(stream);
			}
		} catch (Exception e) {
			return null;
		}
	}

	// ------------------------------------------------------------------

	/**
	 * Reads a private field by name, from the instance's own class and then its superclasses.
	 *
	 * <p>{@code setAccessible} is what makes this work on a field the module system would otherwise
	 * hide; it is tried, and a refusal is a {@code null} answer rather than a throw, so a stricter
	 * runtime costs the fallback and nothing else.</p>
	 */
	private static Object field(Class<?> type, String name) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			try {
				final Field found = current.getDeclaredField(name);
				found.setAccessible(true);
				return found.get(null);
			} catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			} catch (Throwable e) {
				report(e);
				return null;
			}
		}
		return null;
	}

	/** A {@code PackResources}'s own name for itself, for the log line. */
	private static String safeName(PackResources pack) {
		try {
			return pack.packId();
		} catch (Throwable e) {
			return pack.getClass().getSimpleName();
		}
	}

	private static String readAll(Path file) {
		try {
			return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return null;
		}
	}

	private static String readAll(InputStream stream) throws IOException {
		final StringBuilder builder = new StringBuilder();
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = stream.read(buffer)) > 0) {
			builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
		}
		return builder.toString();
	}

	private static void close(List<PackResources> packs) {
		for (PackResources pack : packs) {
			try {
				pack.close();
			} catch (Throwable ignored) {
				/* Closing is best effort: the file handle outlives nothing here. */
			}
		}
	}

	private static void report(Throwable e) {
		if (REPORTED.compareAndSet(false, true)) {
			Joban.LOGGER.warn("[Joban Client] Could not search the resource packs for a script name"
					+ " Minecraft's resource manager cannot index ({}). Scripts whose files carry"
					+ " capitals will be reported as missing.", e.toString());
		}
	}
}
