package com.jsblock.script;

import com.jsblock.Joban;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates every path a PIDS script can talk this mod into reading.
 *
 * <h2>Why this exists</h2>
 * <p>A PIDS preset is a file inside a resource pack, and resource packs arrive with servers,
 * worlds and modpacks — so a script is untrusted code, which is exactly why
 * {@link ScriptClassShutter} keeps it out of {@code java.nio.file}.</p>
 *
 * <p>That guard is not enough on its own, because the scripting surface reads files for the
 * script: {@code include()} pulls a script out of the resource manager, and
 * {@code Texture.texture("ns:path")} pulls an image out of it. Both take a
 * {@link ResourceLocation}, and a resource location happily accepts
 * {@code jsblock:../../../../../../etc/hosts} — dots and slashes are legal path characters. For a
 * <b>folder</b> resource pack (what a pack in {@code resourcepacks/} is, and what a development
 * environment uses for every pack) Minecraft resolves that at the filesystem level, where
 * {@code ..} means what it always means. The script could therefore read any file the game can,
 * one {@code include()} at a time, and get the contents back in the log through a syntax error.</p>
 *
 * <p>This is the port of JCM 2.x's {@code com.lx862.mtrscripting.util.FilesUtil}: its
 * {@code resolvePathSafe} resolves a path under a root and then hands it to
 * {@code ensurePathNotEscaped}, which walks the normalized path's parents and refuses the read if
 * it runs off the top without meeting the root. JCM 2.x needs it for the {@code Files} global,
 * which writes into the game directory; this port has no such global, so the same check is
 * applied to the two reads it does have, and {@link #resolveWithin} is the literal port kept for
 * the callers that do have a real directory — the headless script check, which reads resource
 * roots straight off the disk.</p>
 *
 * <h2>What is refused</h2>
 * <ul>
 *   <li>{@code ..} as a path segment — the escape itself, in any position and any number of times</li>
 *   <li>a leading {@code /} — an absolute path, which would ignore the pack root entirely</li>
 *   <li>a backslash anywhere — a Windows separator, and never valid in a resource path</li>
 *   <li>a drive letter ({@code C:/...}, {@code C:\...}) — caught by the two rules above, and named
 *       in the message so the author is not left guessing which one bit</li>
 *   <li>a path that is empty or only whitespace</li>
 * </ul>
 *
 * <p>Rejection is an {@link RejectedPathException}, which is an
 * {@link IllegalArgumentException} on purpose: {@link ScriptDrawCalls.Texture#texture(String)}
 * sits in a builder chain inside the script's own {@code render()}, so a throw there travels the
 * same path as {@code ctx.draw()} rejecting a call — logged once with the script's stack, shown to
 * the player once, and retried by the engine — instead of taking a renderer down with it.</p>
 */
public final class ScriptPaths {

	/** How many distinct refusals to report before going quiet; a looping script must not flood. */
	private static final Set<String> REJECTED = ConcurrentHashMap.newKeySet();

	private ScriptPaths() {
	}

	/**
	 * Signals a path a script is not allowed to read.
	 *
	 * <p>A distinct type rather than a bare {@code IllegalArgumentException} so the callers that
	 * must log and carry on — {@code include()} runs while a script is being parsed and cannot
	 * throw at the pack — can tell "refused by the sandbox" apart from "the resource was missing",
	 * which are different problems with different fixes.</p>
	 */
	public static final class RejectedPathException extends IllegalArgumentException {
		private final String reference;

		RejectedPathException(String reference, String reason) {
			super("Refused to read \"" + reference + "\": " + reason);
			this.reference = reference;
		}

		/** @return the reference the script asked for, as it wrote it. */
		public String getReference() {
			return reference;
		}
	}

	/**
	 * Validates a script-written reference and returns it unchanged.
	 *
	 * <p>The reference is the {@code namespace:path} form the rest of the scripting surface uses;
	 * without a colon the namespace is {@code minecraft}, exactly as {@code Resources.id()} reads
	 * it. Only the shape is checked here — whether anything exists at that location is the
	 * resource manager's answer, as before.</p>
	 *
	 * @return the reference, so a caller can validate and use in one expression
	 * @throws RejectedPathException when the reference could resolve outside its own pack
	 */
	public static String checkReference(String reference) {
		if (reference == null || reference.trim().isEmpty()) {
			throw new RejectedPathException(String.valueOf(reference), "the path is empty");
		}
		final int colon = reference.indexOf(':');
		final String path = colon < 0 ? reference : reference.substring(colon + 1);
		checkPath(reference, path);
		return reference;
	}

	/**
	 * Validates an already-built location, for the callers that were handed one rather than a
	 * string — {@code Texture.texture(Resources.id(...))} is the one that matters.
	 *
	 * @return the location, so a caller can validate and use in one expression
	 * @throws RejectedPathException when the location could resolve outside its own pack
	 */
	public static ResourceLocation checkLocation(ResourceLocation location) {
		if (location == null) {
			throw new RejectedPathException("null", "no location was given");
		}
		checkPath(location.toString(), location.getPath());
		return location;
	}

	/**
	 * Validates a script-written reference and builds the location from it.
	 *
	 * @throws RejectedPathException when the reference could resolve outside its own pack
	 */
	public static ResourceLocation resource(String reference) {
		return new ResourceLocation(checkReference(reference));
	}

	/**
	 * Builds the location for a script reference, or {@code null} when the reference is not
	 * spelled the way a resource location may be.
	 *
	 * <p>This is the tolerant half of {@link #resource(String)}. A resource location is lower
	 * case by definition, so a pack whose files are named {@code scripts/Digital_Rail.js} — met
	 * transit has exactly that file — describes them with capitals that the vanilla constructor
	 * rejects outright. The path rules still run first and still refuse a {@code ..} or an
	 * absolute path, so tolerance here cannot become an escape; it only decides whether the
	 * reference is usable as written.</p>
	 *
	 * <p>{@link #foldToResourceLocation(String)} is what a caller tries next, and the two
	 * together are why {@code include()} of a capitalised script works at all. Note that this is
	 * a kindness to packs and <b>not</b> Minecraft's rule: the game's own resource manager is
	 * case sensitive, and a pack that spells a name two ways will still only have one of them
	 * resolve anywhere else.</p>
	 *
	 * @return the location, or {@code null} when it cannot be built as written
	 * @throws RejectedPathException when the reference could resolve outside its own pack
	 */
	public static ResourceLocation resourceOrNull(String reference) {
		checkReference(reference);
		try {
			return new ResourceLocation(reference);
		} catch (RuntimeException notALocation) {
			return null;
		}
	}

	/**
	 * Lower-cases a reference so the vanilla constructor accepts it.
	 *
	 * <p>The fallback for {@link #resourceOrNull(String)} returning {@code null}: the reference
	 * was refused only for its spelling, so it is read as the lower-case name a resource location
	 * requires. A pack whose file genuinely carries capitals is found through the resource
	 * manager's own lower-case index, which is what makes this work for a folder pack and a zip
	 * pack alike — see {@link ScriptEngine#caseFoldedCandidates}.</p>
	 *
	 * @throws RejectedPathException when the lowercase form is still not a usable reference
	 */
	public static ResourceLocation foldToResourceLocation(String reference) {
		return new ResourceLocation(checkReference(reference).toLowerCase(java.util.Locale.ROOT));
	}

	private static void checkPath(String reference, String path) {
		if (path.isEmpty()) {
			throw new RejectedPathException(reference, "it names no file");
		}
		/* A backslash is never valid in a resource path, and it is how a Windows path is written:
		   "..\..\x" and "C:\x" both arrive here. Checked before the leading-slash rule so that a
		   drive letter is reported as the Windows path it is. */
		if (path.indexOf('\\') >= 0) {
			throw new RejectedPathException(reference,
					"a resource path cannot contain a backslash (Windows path or drive letter)");
		}
		if (path.charAt(0) == '/') {
			throw new RejectedPathException(reference,
					"it is absolute; a script path is always relative to its own resource pack");
		}
		/* The escape itself. Every segment is examined rather than the whole string, because
		   "a..b" is a legal file name and only a whole segment means the parent directory. */
		for (String segment : path.split("/", -1)) {
			if ("..".equals(segment)) {
				throw new RejectedPathException(reference,
						"it walks out of its own resource pack with \"..\"");
			}
		}
	}

	// ------------------------------------------------------------------
	// The physical-path half, ported from JCM 2.x's FilesUtil
	// ------------------------------------------------------------------

	/**
	 * Resolves {@code elements} under {@code root} and refuses the result if it escaped.
	 *
	 * <p>Literal port of JCM 2.x's {@code FilesUtil.resolvePathSafe}. Kept because the check that
	 * proves the guard works has to run against real directories: the game's reads go through the
	 * resource manager, which takes a location rather than a path, so this is the only place the
	 * physical rule can be exercised — and it is the rule a future {@code Files}-style global
	 * would be built on.</p>
	 *
	 * @throws IOException when the resolved path is not inside {@code root}
	 */
	public static Path resolveWithin(Path root, String... elements) throws IOException {
		Path resolved = root;
		for (String element : elements) {
			resolved = resolved.resolve(element);
		}
		ensureWithin(root, resolved);
		return resolved;
	}

	/**
	 * @param root      the directory a read must stay inside
	 * @param candidate the path about to be used
	 * @throws IOException when {@code candidate} is not {@code root} or below it
	 *
	 * <p>Walks up from the normalized candidate until it meets the root; running out of parents
	 * first means the path climbed out. JCM 2.x's own algorithm and its own wording, so a pack
	 * author who has seen the message in JCM 2.x sees the same one here.</p>
	 */
	public static void ensureWithin(Path root, Path candidate) throws IOException {
		final Path normalizedRoot = root.toAbsolutePath().normalize();
		Path current = candidate.toAbsolutePath().normalize();
		while (current != null) {
			if (current.equals(normalizedRoot)) {
				return;
			}
			current = current.getParent();
		}
		throw new IOException(String.format("Path must be within the \"%s\" directory!",
				candidate.getFileName()));
	}

	/**
	 * Reports a refused read once per distinct reference.
	 *
	 * <p>Console first, because the message has to reach whoever wrote the pack, and then the
	 * player, through {@link ScriptEngine#ERROR_NOTIFIER} — the same two-step the script engine
	 * uses for a throw, and for the same reason: this happens mid-frame, where talking to the
	 * player is not safe. Repeats are dropped because the offending call is normally in a
	 * per-frame path.</p>
	 */
	public static void report(String api, RejectedPathException rejected) {
		final String reference = rejected.getReference();
		if (!REJECTED.add(api + "|" + reference)) {
			return;
		}
		Joban.LOGGER.error("[Joban Client] A PIDS script asked {} for \"{}\", which is outside its own"
				+ " resource pack. Refused; a script may only read its own files. {}", api, reference,
				rejected.getMessage());
		ScriptEngine.ERROR_NOTIFIER.queue(() -> {
			if (!com.jsblock.client.ClientConfig.isScriptErrorNotificationEnabled()) {
				return;
			}
			final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
			if (minecraft == null || minecraft.player == null) {
				return;
			}
			minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
					"\u00a7c[Joban Client] \u00a7fA PIDS script tried to read outside its resource pack: \u00a7c"
							+ shorten(reference)), false);
		});
	}

	/** Keeps a chat line to something a chat window can show. */
	private static String shorten(String reference) {
		return reference.length() <= 64 ? reference : reference.substring(0, 61) + "...";
	}

	/** Forgets which refusals have been reported; called when the resource manager reloads. */
	public static void reset() {
		REJECTED.clear();
	}

	/**
	 * @return how many distinct references have been refused since the last {@link #reset()}.
	 *
	 * <p>For the headless check: the console line proves nothing on its own, and the player-facing
	 * half of a refusal is a queued chat message that a check never delivers. This is the observable
	 * the check asserts on.</p>
	 */
	public static int rejectedCount() {
		return REJECTED.size();
	}
}
