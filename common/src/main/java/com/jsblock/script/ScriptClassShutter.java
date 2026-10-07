package com.jsblock.script;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Limits which Java classes a PIDS script can reach.
 *
 * <p>A PIDS preset is a file inside a resource pack. Resource packs arrive with servers,
 * worlds and modpacks, so a preset script is untrusted code: without a shutter, Rhino hands
 * a script the whole JVM, and {@code java.nio.file.Files} is one call away. This keeps a
 * preset able to draw a departure board and nothing else.</p>
 *
 * <p>Port of JCM 2.x's {@code com.lx862.mtrscripting.core.api.MTRClassShutter}, with the
 * allow-list retargeted from {@code com.lx862.mtrscripting} onto this branch's scripting
 * package. JCM 2.x lets the player switch it off from the config screen, and shows
 * {@code ScriptRestrictionWarningScreen} first; both behaviours are kept.</p>
 *
 * <p>Rules are either an exact class name or {@code package.*}. Deny rules are consulted
 * only after an allow rule has matched, so {@code java.lang.*} can be allowed while
 * {@code java.lang.System} and {@code java.lang.Class} stay out of reach.</p>
 */
public final class ScriptClassShutter implements ClassShutter {

	/** When {@code false} everything except this class itself is visible, exactly as JCM 2.x behaves. */
	private boolean enabled = true;

	private final List<String> allowed = new ArrayList<>();
	private final List<String> denied = new ArrayList<>();

	public ScriptClassShutter() {
		allow(
				// What a preset legitimately touches: numbers, text, collections, time, images.
				"java.awt.*",
				"java.time.*",
				"java.lang.*",
				"java.math.BigDecimal",
				"java.math.BigInteger",
				"java.util.*",
				"java.text.*",
				"javax.imageio.*",
				"sun.java2d.*",
				"sun.font.*",
				"sun.awt.*",
				"java.io.Closeable",
				"java.io.InputStream",
				"java.io.OutputStream",
				"jdk.*",
				/* ResourceLocation is the one Minecraft type the scripting surface hands back:
				   Resources.id() returns it. Rhino wraps return values through the shutter too,
				   so without this even jsblock:scripts/pids_util.js fails to load -- which the
				   headless check caught the first time the shutter was switched on. It is a
				   namespace/path pair and grants nothing else. */
				"net.minecraft.resources.ResourceLocation",
				// This branch's scripting surface: Text/Texture/Rectangle, PIDSWrapper, helpers.
				"com.jsblock.script.*",
				// Rhino's own runtime classes are needed for the engine to function at all.
				"org.mozilla.*"
		);
		deny(
				// Escape hatches out of the sandbox.
				"java.lang.invoke.*",
				"java.lang.reflect.*",
				"java.lang.Process",
				"java.lang.ProcessBuilder",
				"java.lang.Class",
				"java.lang.ClassLoader",
				"java.lang.Shutdown",
				"java.lang.SecurityManager",
				"java.lang.System",
				"java.lang.Runtime",
				"java.util.jar.*",
				"java.util.zip.*",
				"jdk.internal.*"
		);
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void allow(String... classRules) {
		allowed.addAll(Arrays.asList(classRules));
	}

	public void deny(String... classRules) {
		denied.addAll(Arrays.asList(classRules));
	}

	@Override
	public boolean visibleToScripts(String fullClassName) {
		/* Hiding the shutter itself keeps a script from reading the rules and, more to the
		   point, from reaching the engine through it. */
		return !ScriptClassShutter.class.getName().equals(fullClassName) && isAllowed(fullClassName);
	}

	private boolean isAllowed(String className) {
		if (!enabled) {
			return true;
		}

		/* Array types arrive as "[Ljava.lang.String;" or "[I"; primitives are always fine,
		   and an object array is judged by its element type. */
		if (className.startsWith("[")) {
			final String type = className.substring(className.lastIndexOf('[') + 1);
			if (type.length() == 1) {
				return true;
			}
			if (type.startsWith("L") && type.endsWith(";")) {
				className = type.substring(1, type.length() - 1);
			}
		}

		for (String rule : allowed) {
			if (matches(rule, className)) {
				for (String denyRule : denied) {
					if (matches(denyRule, className)) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String rule, String className) {
		if (rule.endsWith(".*")) {
			final String prefix = rule.substring(0, rule.length() - 1); // keep the dot, so "java.util." cannot match "java.utilX"
			return className.startsWith(prefix);
		}
		return rule.equals(className);
	}

	/**
	 * Installs the shutter on a context that is about to run a script.
	 *
	 * <p>Rhino allows a context's shutter to be set once, and answers a second attempt with a
	 * {@link SecurityException} -- not the {@code IllegalStateException} its own javadoc
	 * suggests. That distinction mattered: this method used to catch only the latter, so the
	 * exception escaped into {@code Program}'s constructor, which calls {@code create()} while
	 * the compile-time context is still entered. In game it surfaced as every scripted preset
	 * reporting <em>"CRT PIDS (Style 1) threw in create(): Cannot overwrite existing
	 * ClassShutter object"</em>, on a panel that then drew correctly anyway.</p>
	 *
	 * <p>Re-installing is not an error worth propagating either way: the shutter is a single
	 * instance whose enabled flag is mutable, so a context that already carries it is already
	 * configured correctly. Any runtime failure here is swallowed deliberately -- refusing to
	 * run a preset because the sandbox was installed twice would be the worse outcome.</p>
	 */
	public static void install(Context cx, ScriptClassShutter shutter) {
		if (cx == null || shutter == null) {
			return;
		}
		try {
			cx.setClassShutter(shutter);
		} catch (RuntimeException alreadyConfigured) {
			// Already set on this context; the instance is shared and its flag is live, so the
			// earlier install is still the one enforcing the rules.
			LAST_INSTALL_SKIPPED = alreadyConfigured;
		}
	}

	/**
	 * The exception the last {@link #install} swallowed, or {@code null}.
	 *
	 * <p>Kept so the headless check can assert that a repeat install is refused <em>and</em>
	 * handled, rather than merely that it does not throw.</p>
	 */
	private static volatile RuntimeException LAST_INSTALL_SKIPPED;

	/** @return the exception the most recent {@link #install} swallowed, or {@code null}. */
	public static RuntimeException lastInstallSkipped() {
		return LAST_INSTALL_SKIPPED;
	}
}
