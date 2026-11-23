package com.jsblock.script;

import java.nio.file.Path;

/**
 * The hooks {@code FilesCheck} needs from {@link FilesUtil}, which are package-private so that a
 * script cannot reach them.
 *
 * <p>{@link FilesUtil} is put on a script's scope as a {@code NativeJavaClass}, and Rhino exposes
 * every {@code public static} member of such a class to the script — confirmed by
 * {@code FilesCheck}'s own probe, which found a public override reachable. So the testing hooks
 * cannot be public members of the class the global points at, and this class exists to hold them
 * instead: it lives in the same package, so it can reach the package-private fields, and it is
 * never registered as a global, so no preset can name it.</p>
 *
 * <p>Only the check uses it, which is why it is here rather than in the mod's own tree.</p>
 */
final class FilesCheckSupport {

	private FilesCheckSupport() {
	}

	/** Redirects both of {@link FilesUtil}'s roots under {@code root}. */
	static void redirectTo(Path root) {
		FilesUtil.useRootForTesting(root);
	}

	/** Puts both roots back to the game directory. */
	static void reset() {
		FilesUtil.resetForTesting();
	}

	/** @return the data area, {@code <root>/data/mtrscripting}. */
	static Path dataDirectory() {
		return FilesUtil.dataDirectory();
	}

	/** @return the root {@code read()} resolves against. */
	static Path readDirectory() {
		return FilesUtil.readDirectory();
	}
}
