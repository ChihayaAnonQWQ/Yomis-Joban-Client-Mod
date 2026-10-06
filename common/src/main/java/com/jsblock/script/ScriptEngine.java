package com.jsblock.script;

import com.jsblock.Joban;
import com.jsblock.data.PIDSPreset;
import mtr.MTRClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the JavaScript PIDS presets used by JCM 2.x on top of MTR 3.
 *
 * <p>A JCM 2.x preset is a {@code scriptFiles} list rather than a list of JSON components.
 * Each script defines {@code create(ctx, state, pids)}, {@code render(ctx, state, pids)} and
 * {@code dispose(ctx, state, pids)}, and draws by chaining builders such as
 * {@code Text.create("Clock").text(...).pos(124, 6).rightAlign().draw(ctx)}.</p>
 *
 * <p>Port of JCM 2.x's {@code com.lx862.mtrscripting} core, reduced to the PIDS surface:
 * JCM 2.x also drives vehicles, lifts, eye-candy and networking from scripts, none of which
 * this branch needs. The globals registered here are the ones JCM 2.x's own
 * {@code pids_1a.js} and the shipped {@code pids_util.js} touch.</p>
 */
public final class ScriptEngine {

	/** Compiled programs, keyed by preset id. Cleared whenever resources reload. */
	private static final Map<String, Program> PROGRAMS = new HashMap<>();

	/** Scripts currently being included, so a cycle cannot recurse forever. */
	private static final Set<String> INCLUDE_STACK = new HashSet<>();

	private ScriptEngine() {
	}

	// ==================================================================
	// Compilation
	// ==================================================================

	/**
	 * @return the compiled program for a preset, compiling and caching it on first use.
	 * Returns {@code null} when the preset has no scripts or none of them compiled.
	 */
	public static Program programFor(PIDSPreset preset, net.minecraft.core.BlockPos pos) {
		if (preset == null || !preset.isScripted()) {
			return null;
		}
		/* JCM 2.x keys script instances by UniqueKey("pids", getId(), x, y, z), so each
		   PIDS block owns its state. Sharing one state between blocks -- and between the
		   two halves of a panel, which each get a renderer call -- runs a preset's frame
		   counters at double speed and lets two draws of the same panel disagree. */
		final String key = preset.displayName() + "@" + preset.id + (pos == null ? "" : "#" + pos.asLong());
		final Program cached = PROGRAMS.get(key);
		if (cached != null) {
			return cached;
		}
		final Program program = compile(key, preset);
		if (program != null) {
			PROGRAMS.put(key, program);
		}
		return program;
	}

	private static Program compile(String key, PIDSPreset preset) {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = newScope(cx);

			boolean anyLoaded = false;
			for (String scriptFile : preset.scriptFiles) {
				if (evaluateResource(cx, scope, scriptFile)) {
					anyLoaded = true;
				}
			}
			if (!anyLoaded) {
				Joban.LOGGER.warn("[Joban Client] PIDS preset \"{}\" lists scripts but none could be read.", preset.id);
				return null;
			}

			return new Program(key, scope);
		} catch (Exception e) {
			Joban.LOGGER.error("[Joban Client] Failed to compile PIDS scripts for \"" + preset.id + "\": " + e);
			return null;
		} finally {
			Context.exit();
		}
	}

	/** Drops every compiled program; call when the resource manager reloads. */
	public static void reset() {
		PROGRAMS.clear();
		/* Script textures are keyed by the identifiers the old pack used, so they must go too. */
		ScriptTextures.reset();
	}

	/**
	 * Builds a Rhino scope with the PIDS globals registered.
	 *
	 * <p>Exposed so the headless script check can compile a real preset script through exactly
	 * the same globals the game registers, and therefore fail on the same missing-API mistakes
	 * a player would hit.</p>
	 */
	public static Scriptable newScope(Context cx) {
		cx.setLanguageVersion(Context.VERSION_ES6);
		cx.setOptimizationLevel(-1); // Rhino's interpreter: avoids class generation issues on a mod classloader.
		final Scriptable scope = cx.initStandardObjects();
		registerGlobals(cx, scope);
		return scope;
	}

	/**
	 * Reads and evaluates a script from the client resource manager.
	 *
	 * @param location a resource location without the {@code .js} extension, e.g.
	 *                 {@code jsblock:scripts/pids_util.js}
	 * @return {@code true} when the script existed and evaluated
	 */
	static boolean evaluateResource(Context cx, Scriptable scope, String location) {
		if (location == null || location.isEmpty() || !INCLUDE_STACK.add(location)) {
			return false;
		}
		try {
			final ResourceLocation id = new ResourceLocation(location);
			final String source = readResource(id);
			if (source == null || source.trim().isEmpty()) {
				Joban.LOGGER.warn("[Joban Client] PIDS script {}:{} is missing or empty.",
						id.getNamespace(), id.getPath());
				return false;
			}
			cx.evaluateString(scope, source, id.toString(), 1, null);
			return true;
		} catch (Exception e) {
			Joban.LOGGER.error("[Joban Client] Error evaluating PIDS script " + location + ": " + e);
			return false;
		} finally {
			INCLUDE_STACK.remove(location);
		}
	}

	/**
	 * Reads a script's text from the client resource manager.
	 *
	 * <p>Scripts live under {@code assets/<namespace>/<path>}, the same place JCM 2.x reads
	 * them from, so a JCM 2.x resource pack needs no changes.</p>
	 */
	private static String readResource(ResourceLocation id) {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return null;
		}
		final ResourceLocation full = new ResourceLocation(id.getNamespace(), id.getPath());
		try (InputStream stream = minecraft.getResourceManager().getResource(full).orElseThrow().open()) {
			final StringBuilder builder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					builder.append(line).append('\n');
				}
			}
			return builder.toString();
		} catch (Exception e) {
			return null;
		}
	}

	// ==================================================================
	// Globals
	// ==================================================================

	private static void registerGlobals(Context cx, Scriptable scope) {
		ScriptableObject.putProperty(scope, "Resources", new NativeJavaClass(scope, Resources.class));
		ScriptableObject.putProperty(scope, "TextUtil", new NativeJavaClass(scope, TextUtil.class));
		ScriptableObject.putProperty(scope, "MinecraftClient", new NativeJavaClass(scope, MinecraftClient.class));
		ScriptableObject.putProperty(scope, "Text", new NativeJavaClass(scope, ScriptDrawCalls.Text.class));
		ScriptableObject.putProperty(scope, "Texture", new NativeJavaClass(scope, ScriptDrawCalls.Texture.class));
		ScriptableObject.putProperty(scope, "Rectangle", new NativeJavaClass(scope, ScriptDrawCalls.Rectangle.class));

		ScriptableObject.putProperty(scope, "print", new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
				final StringBuilder builder = new StringBuilder();
				for (Object arg : args) {
					if (builder.length() > 0) {
						builder.append(' ');
					}
					builder.append(Context.toString(arg));
				}
				Joban.LOGGER.info("[Joban Client] [PIDS script] {}", builder);
				return Undefined.instance;
			}
		});

		/* include(Resources.id("jsblock:scripts/pids_util.js")) pulls another script into the
		   same scope. JCM 2.x evaluates includes while parsing; doing it through a real
		   function keeps the same semantics for scripts that include conditionally. */
		ScriptableObject.putProperty(scope, "include", new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
				if (args.length > 0 && args[0] != null) {
					evaluateResource(context, s, Context.toString(args[0]));
				}
				return Undefined.instance;
			}
		});
	}

	// ==================================================================
	// JS-facing global helpers
	// ==================================================================

	/** {@code Resources.id("namespace:path")} — the JCM 2.x way to reference a resource. */
	public static final class Resources {
		private Resources() {
		}

		public static ResourceLocation id(String namespacePath) {
			final int colon = namespacePath == null ? -1 : namespacePath.indexOf(':');
			if (colon < 0) {
				return new ResourceLocation("minecraft", String.valueOf(namespacePath));
			}
			return new ResourceLocation(namespacePath.substring(0, colon), namespacePath.substring(colon + 1));
		}
	}

	/** {@code TextUtil.cycleString("a|b")} — alternates the parts as the game ticks. */
	public static final class TextUtil {
		/** Ticks each variant is shown for when a script does not say. */
		public static final int DEFAULT_SWITCH_TICKS = 60;

		private TextUtil() {
		}

		public static String cycleString(String text) {
			return cycleString(text, DEFAULT_SWITCH_TICKS);
		}

		/**
		 * @param text          pipe-separated variants, e.g. {@code "即將到達|Arriving"}
		 * @param switchTicks   ticks to show each variant for
		 * @return the variant for the current tick
		 */
		public static String cycleString(String text, int switchTicks) {
			if (text == null) {
				return "";
			}
			if (text.indexOf('|') < 0) {
				return text;
			}
			/* "||" is an escaped single pipe in MTR's own strings, so a variant may itself
			   be empty; splitting on a single pipe keeps that behaviour. */
			final String[] variants = text.split("\\|", -1);
			final int ticks = Math.max(1, switchTicks);
			final int index = (int) ((currentTick() / ticks) % variants.length);
			return variants[index];
		}

		/**
		 * @return the client game tick, falling back to wall-clock ticks when MTR's counter is
		 * not reachable. The headless script check runs outside a game, and a PIDS that fails to
		 * cycle is better than one that throws inside the renderer.
		 */
		private static long currentTick() {
			try {
				return (long) MTRClient.getGameTick();
			} catch (Throwable t) {
				return System.currentTimeMillis() / 50L;
			}
		}

		/*
		 * The part-splitting helpers below are ported from JCM 2.x's TextUtilJS
		 * (fabric/src/main/java/com/lx862/mtrscripting/mod/impl/mtr/util/TextUtilJS.java),
		 * MIT License, Copyright (c) 2022-present Zbx1425. They are reproduced here rather
		 * than reimplemented so a preset that depends on their exact splitting keeps working.
		 *
		 * MTR strings carry two separators: "|" between language variants, and "||" between
		 * the main text and an extra part. getNonCjkParts("抵達|Arriving") returns "Arriving",
		 * which is how the NYC preset picks the Latin half to draw.
		 */

		/** {@code TextUtil.getCjkParts(s)} — the variant that is Chinese. */
		public static String getCjkParts(String src) {
			return getCjkMatching(src, true);
		}

		/** {@code TextUtil.getNonCjkParts(s)} — the variant that is not Chinese. */
		public static String getNonCjkParts(String src) {
			return getCjkMatching(src, false);
		}

		/** {@code TextUtil.getExtraParts(s)} — the part after the "||" separator. */
		public static String getExtraParts(String src) {
			return getExtraMatching(src, true);
		}

		/** {@code TextUtil.getNonExtraParts(s)} — the part before the "||" separator. */
		public static String getNonExtraParts(String src) {
			return getExtraMatching(src, false);
		}

		/** {@code TextUtil.getNonCjkAndExtraParts(s)}. */
		public static String getNonCjkAndExtraParts(String src) {
			final String extraParts = getExtraMatching(src, true).trim();
			return getCjkMatching(src, false).trim() + (extraParts.isEmpty() ? "" : "|" + extraParts);
		}

		/** {@code TextUtil.isCjk(s)}. */
		public static boolean isCjk(String src) {
			return src != null && mtr.data.IGui.isCjk(src);
		}

		private static String getExtraMatching(String src, boolean extra) {
			if (src == null) {
				return "";
			}
			if (src.contains("||")) {
				return src.split("\\|\\|", 2)[extra ? 1 : 0].trim();
			}
			return extra ? "" : src;
		}

		private static String getCjkMatching(String src, boolean cjk) {
			if (src == null) {
				return "";
			}
			if (src.contains("||")) {
				src = src.split("\\|\\|", 2)[0];
			}
			final StringBuilder result = new StringBuilder();
			for (String part : src.split("\\|", -1)) {
				if (mtr.data.IGui.isCjk(part) == cjk) {
					if (result.length() > 0) {
						result.append(' ');
					}
					result.append(part);
				}
			}
			return result.toString().trim();
		}
	}

	/** {@code MinecraftClient.worldDayTime()} — the value JCM 2.x hands to PIDSUtil.formatTime. */
	public static final class MinecraftClient {
		private MinecraftClient() {
		}

		public static long worldDayTime() {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.level == null) {
				return 0L;
			}
			return minecraft.level.getDayTime();
		}

		/** {@code MinecraftClient.worldIsRaining()} — the HKR presets branch on this. */
		public static boolean worldIsRaining() {
			final Minecraft minecraft = Minecraft.getInstance();
			return minecraft != null && minecraft.level != null && minecraft.level.isRaining();
		}

		/** {@code MinecraftClient.worldIsThundering()}. */
		public static boolean worldIsThundering() {
			final Minecraft minecraft = Minecraft.getInstance();
			return minecraft != null && minecraft.level != null && minecraft.level.isThundering();
		}
	}

	// ==================================================================
	// Program
	// ==================================================================

	/** A compiled preset script, ready to be asked to render frames. */
	public static final class Program {

		private final String key;
		private final Scriptable scope;
		/** Per-program script state, the {@code state} argument JCM 2.x hands to the script. */
		private final ScriptableObject state;
		/** Distinct failures already reported, so a per-frame throw cannot flood the log. */
		private final Set<String> reportedErrors = new HashSet<>();

		Program(String key, Scriptable scope) {
			this.key = key;
			this.scope = scope;
			this.state = new ScriptableObject() {
				@Override
				public String getClassName() {
					return "PIDSState";
				}
			};
			state.setPrototype(ScriptableObject.getObjectPrototype(scope));
			state.setParentScope(scope);
			invoke("create", null, null);
		}

		public String getKey() {
			return key;
		}

		private boolean isDefined(String function) {
			return scope.get(function, scope) instanceof org.mozilla.javascript.Function;
		}

		private Object invoke(String function, ScriptRenderContext ctx, PIDSWrapper pids) {
			return invokeInternal(function, ctx, pids, false);
		}

		/** @return {@code true} when the function ran without throwing. */
		private boolean invokeChecked(String function, ScriptRenderContext ctx, PIDSWrapper pids) {
			return (Boolean) invokeInternal(function, ctx, pids, true);
		}

		private Object invokeInternal(String function, ScriptRenderContext ctx, PIDSWrapper pids, boolean returnSuccess) {
			if (!isDefined(function)) {
				return returnSuccess ? Boolean.TRUE : Undefined.instance;
			}
			final Context cx = Context.enter();
			try {
				cx.setLanguageVersion(Context.VERSION_ES6);
				cx.setOptimizationLevel(-1);
				final Object fn = scope.get(function, scope);
				/* Always three arguments, so a script that ignores ctx (as create/dispose
				   usually do) still receives its state and pids in the right positions. */
				final Object[] args = new Object[]{ctx, state, pids};
				final Object result = ((org.mozilla.javascript.Function) fn).call(cx, scope, scope, args);
				return returnSuccess ? Boolean.TRUE : result;
			} catch (Exception e) {
				/* A script that throws keeps throwing every frame -- the earlier build wrote
				   ~8 MB of log in a few seconds this way. Report the first occurrence of each
				   distinct failure with enough context to act on it, then stay quiet until the
				   next resource reload recompiles the program. */
				final String signature = function + "|" + e.getClass().getSimpleName() + "|" + e.getMessage();
				if (reportedErrors.add(signature)) {
					Joban.LOGGER.error("[Joban Client] PIDS script \"{}\" threw in {}(): {}", key, function, e.toString());
					if (pids != null) {
						Joban.LOGGER.error("[Joban Client]   arrivals available: {}, rows: {}, preset: {}"
										+ " -- a script that indexes past the end gets null, and anything after the"
										+ " throw (including its background) is never drawn",
								pids.arrivals().size(), pids.rows, pids.type);
					}
					Joban.LOGGER.error("[Joban Client]   (repeated failures of this kind are suppressed until reload)");
				}
				return returnSuccess ? Boolean.FALSE : Undefined.instance;
			} finally {
				Context.exit();
			}
		}

		/** Runs the script's {@code render(ctx, state, pids)} for one frame. */
		public void render(ScriptRenderContext ctx, PIDSWrapper pids) {
			renderOrFail(ctx, pids);
		}

		/**
		 * @return {@code false} when the script threw, meaning part of the frame — very likely
		 * its background, which scripts normally draw first — was never issued
		 */
		public boolean renderOrFail(ScriptRenderContext ctx, PIDSWrapper pids) {
			return invokeChecked("render", ctx, pids);
		}

		/** Runs the script's {@code dispose(ctx, state, pids)}; used when a preset is dropped. */
		public void dispose() {
			invoke("dispose", null, null);
		}

		/** @return the list of function names the script defined, for diagnostics. */
		public List<String> definedFunctions() {
			final List<String> names = new ArrayList<>();
			for (String candidate : new String[]{"create", "render", "dispose"}) {
				if (isDefined(candidate)) {
					names.add(candidate);
				}
			}
			return names;
		}
	}
}
