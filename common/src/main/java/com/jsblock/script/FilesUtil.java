package com.jsblock.script;

import com.jsblock.Joban;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@code Files} global: the one place a PIDS script can keep something between sessions.
 *
 * <h2>What it is</h2>
 * <p>A JCM 2.x preset that draws a running board has to remember what it already showed — which
 * services have departed, which page of a rotation it is on — and a PIDS panel is redrawn from
 * scratch every frame, so a script's own variables are gone by the next tick unless it writes them
 * out. JCM 2.x answers that with {@code com.lx862.mtrscripting.util.FilesUtil}, registered as the
 * global {@code Files}; this is that class, same method names and same signatures, so a preset
 * written against v2's documentation runs here unchanged:</p>
 *
 * <pre>
 * if (Files.hasData("met_running_board", fileName)) {
 *     let saved = JSON.parse(Files.readData("met_running_board", fileName));
 * }
 * Files.saveData(JSON.stringify(state), "met_running_board", fileName);
 * </pre>
 *
 * <p>That example is not invented — it is {@code met transit}'s
 * {@code assets/jsblock/scripts/met_running_board.js}, the one preset in the packs surveyed for
 * this port that reaches for the global, and the reason it exists here at all.</p>
 *
 * <h2>Where it writes</h2>
 * <p>The same two directories JCM 2.x uses, so a pack that keeps data across the two mods finds
 * its own files:</p>
 * <ul>
 *   <li>{@link #read} — relative to the game directory, the escape hatch for reading a file that
 *       is not the script's own data ({@code Files.read("config", "something.json")}).</li>
 *   <li>{@link #readData} / {@link #saveData} / {@link #deleteData} / {@link #hasData} — under
 *       {@code <game directory>/data/mtrscripting/}, the private area.</li>
 * </ul>
 *
 * <p>Each path element is joined with {@link Path#resolve(String)} and the result is then handed
 * to {@link ScriptPaths#ensureWithin}, so no combination of arguments can reach outside those two
 * roots. That is JCM 2.x's own {@code resolvePathSafe} / {@code ensurePathNotEscaped} pair, and it
 * is not optional here: a PIDS preset arrives inside a resource pack, which arrives with a server
 * or a modpack, so {@code saveData(content, "..", "..", "..", "options.txt")} has to be a refusal
 * and not a write.</p>
 *
 * <h2>Failure is soft</h2>
 * <p>Every method throws {@link IOException} the way JCM 2.x's do, because a preset that calls
 * {@code Files.readData} and gets {@code null} when the file exists but is unreadable would keep
 * old data silently. The throw is caught where every other script throw is caught — the renderer
 * logs it once, tells the player once, and retries the frame — so a broken save degrades the panel
 * rather than the frame.</p>
 */
public final class FilesUtil {

	/** The private area under the game directory, JCM 2.x's own name for it. */
	private static final String DATA_DIRECTORY = "data";

	/** Second half of {@link #DATA_DIRECTORY}; matched to JCM 2.x so the two mods share files. */
	private static final String DATA_NAMESPACE = "mtrscripting";

	/** Set once so a headless run says why it is writing into the working directory. */
	private static final AtomicBoolean DIRECTORIES_REPORTED = new AtomicBoolean();

	/**
	 * Overrides the root both halves resolve against, so a check does not write into a player's
	 * {@code .minecraft}.
	 *
	 * <p>Only a class in this package can set it, which is the point: the global a script sees is
	 * this class, and Rhino exposes every {@code public static} member of a
	 * {@code NativeJavaClass} — so a {@code public} override here would be a way for a preset to
	 * choose where its own files go. Package-private costs the check nothing (it is in this
	 * package too) and keeps the whole surface a script can reach to the four methods v2
	 * documents.</p>
	 */
	static volatile Path rootOverride;

	private FilesUtil() {
	}

	/**
	 * {@code Files.read(a, b, ...)} — a UTF-8 text file relative to the game directory.
	 *
	 * @return the file's text, or {@code null} when it does not exist
	 * @throws IOException when the path leaves the game directory, or the file cannot be read
	 */
	public static String read(String... path) throws IOException {
		return readText(rootMinecraftPath(), path);
	}

	/**
	 * {@code Files.readData(a, b, ...)} — a UTF-8 text file from this mod's data area.
	 *
	 * @return the file's text, or {@code null} when it does not exist
	 * @throws IOException when the path leaves the data directory, or the file cannot be read
	 */
	public static String readData(String... path) throws IOException {
		return readText(dataPath(), path);
	}

	/**
	 * {@code Files.saveData(text, a, b, ...)} — writes UTF-8 text into this mod's data area,
	 * creating the directories above it.
	 *
	 * <p>JCM 2.x writes through {@code FileUtils.writeStringToFile}, which creates the parent
	 * directories; doing the same here is what makes {@code saveData(text, "met_running_board",
	 * name)} work on the first run, when the directory does not exist yet.</p>
	 *
	 * @throws IOException when the path leaves the data directory, or the file cannot be written
	 */
	public static void saveData(String content, String... path) throws IOException {
		final Path file = resolvePathSafe(dataPath(), path);
		final Path parent = file.getParent();
		if (parent != null) {
			java.nio.file.Files.createDirectories(parent);
		}
		java.nio.file.Files.write(file, String.valueOf(content).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * {@code Files.deleteData(a, b, ...)} — removes a file from this mod's data area.
	 *
	 * <p>Never throws for a file that is not there, which is JCM 2.x's
	 * {@code Files.deleteIfExists} and the behaviour a preset wants: clearing state that was
	 * never written is not an error.</p>
	 *
	 * @throws IOException when the path leaves the data directory
	 */
	public static void deleteData(String... path) throws IOException {
		java.nio.file.Files.deleteIfExists(resolvePathSafe(dataPath(), path));
	}

	/**
	 * {@code Files.hasData(name)} / {@code Files.hasData(parent, name)} — whether a file exists in
	 * this mod's data area.
	 *
	 * <p>The second arity is the one the shipped packs call; it is a separate method rather than a
	 * caller-side array because Rhino resolves JavaScript arguments onto a Java varargs parameter
	 * itself (verified against Rhino 1.7.15), so {@code hasData("a", "b")} lands in the varargs form
	 * without a wrapper. Both spellings therefore answer.</p>
	 *
	 * @throws IOException when the path leaves the data directory
	 */
	public static boolean hasData(String... path) throws IOException {
		return java.nio.file.Files.exists(resolvePathSafe(dataPath(), path));
	}

	// ------------------------------------------------------------------
	// Internals
	// ------------------------------------------------------------------

	private static String readText(Path root, String... path) throws IOException {
		final Path file = resolvePathSafe(root, path);
		if (!java.nio.file.Files.isRegularFile(file)) {
			return null;
		}
		return new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	/**
	 * Resolves {@code path} under {@code root} and refuses the result if it escaped.
	 *
	 * <p>Literal port of JCM 2.x's {@code FilesUtil.resolvePathSafe}, including the order of the
	 * checks: join first, then walk the joined path back up to the root. A
	 * {@link ScriptPaths#ensureWithin} refusal is an {@link IOException} with JCM 2.x's own
	 * wording, so an author who has seen the message in v2 sees the same one here.</p>
	 */
	private static Path resolvePathSafe(Path root, String... path) throws IOException {
		Path resolved = root;
		if (path != null) {
			for (String element : path) {
				if (element == null) {
					throw new IOException("Path must be within the \""
							+ root.getFileName() + "\" directory! (a null path element was given)");
				}
				resolved = resolved.resolve(element);
			}
		}
		ScriptPaths.ensureWithin(root, resolved);
		return resolved;
	}

	/**
	 * @return the directory this mod's data files live under, created on first use.
	 *
	 * <p>Derived from the game directory rather than cached in a static initialiser the way
	 * JCM 2.x does it. JCM 2.x's {@code FilesUtil} reads
	 * {@code Minecraft.getInstance().gameDirectory} in its static block, which means the class
	 * cannot be loaded at all without a client — and this port has a headless check that has to
	 * put the {@code Files} global on a scope and prove the path guard works. Resolving lazily
	 * also survives a game directory that changes between runs, which is what happens when the
	 * check points {@link #rootOverride} somewhere else.</p>
	 */
	private static Path dataPath() {
		return rootMinecraftPath().resolve(DATA_DIRECTORY).resolve(DATA_NAMESPACE);
	}

	private static Path rootMinecraftPath() {
		final Path override = rootOverride;
		if (override != null) {
			return override;
		}
		final Path root = locateGameDirectory();
		if (DIRECTORIES_REPORTED.compareAndSet(false, true)) {
			Joban.LOGGER.info("[Joban Client] PIDS scripts write their data under {}", root.resolve(DATA_DIRECTORY).resolve(DATA_NAMESPACE));
		}
		return root;
	}

	/**
	 * @return the game directory, or the working directory when there is no client.
	 *
	 * <p>In game this is {@code .minecraft} and identical to JCM 2.x. Headless — the check, and
	 * any future server-side use — there is no {@code Minecraft} instance to ask, and the honest
	 * answer is "wherever this process was started from" rather than a crash inside a static
	 * initialiser.</p>
	 */
	private static Path locateGameDirectory() {
		try {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null && minecraft.gameDirectory != null) {
				return minecraft.gameDirectory.toPath();
			}
		} catch (Throwable ignored) {
			/* No client at all: Minecraft's own class initialisation is what throws here, and it is
			   not an error worth reporting -- the fallback below is the answer either way. */
		}
		return java.nio.file.Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
	}

	// ------------------------------------------------------------------
	// For the headless check, package-private so a script cannot reach it
	// ------------------------------------------------------------------

	/**
	 * Points both roots at {@code root} for the life of the process.
	 *
	 * <p>Package-private on purpose — see {@link #rootOverride}. {@code FilesCheck} calls it
	 * directly, being in this package.</p>
	 */
	static void useRootForTesting(Path root) {
		rootOverride = root == null ? null : root.toAbsolutePath().normalize();
	}

	/** Restores the game-directory behaviour after {@link #useRootForTesting(Path)}. */
	static void resetForTesting() {
		rootOverride = null;
	}

	/** @return where this mod's data files would go right now, for a check to assert on. */
	static Path dataDirectory() {
		return dataPath();
	}

	/** @return the directory {@link #read} resolves against, for a check to assert on. */
	static Path readDirectory() {
		return rootMinecraftPath();
	}
}
