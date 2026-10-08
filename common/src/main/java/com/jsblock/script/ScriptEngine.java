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

	/**
	 * Player-facing script failures, delivered from the client tick rather than from inside
	 * a block-entity renderer. Port of JCM 2.x's {@code ScriptManager.scriptErrorNotifier}.
	 */
	public static final ScriptErrorNotifier ERROR_NOTIFIER = new ScriptErrorNotifier();

	/** The Rhino class shutter. A live instance so the config screen can switch it off. */
	private static final ScriptClassShutter SHUTTER = new ScriptClassShutter();

	private ScriptEngine() {
	}

	/** @return the shutter guarding every script scope, so its enabled flag can be toggled. */
	public static ScriptClassShutter shutter() {
		return SHUTTER;
	}

	/** @return every compiled program. Used by the debug overlay; not a copy on purpose. */
	public static java.util.Collection<Program> programs() {
		return PROGRAMS.values();
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
		final Program program = compile(key, preset, pos);
		if (program != null) {
			PROGRAMS.put(key, program);
		}
		return program;
	}

	private static Program compile(String key, PIDSPreset preset, net.minecraft.core.BlockPos pos) {
		final Context cx = Context.enter();
		try {
			final Scriptable scope = newScope(cx);

			boolean anyLoaded = false;

			/* JCM 2.x hands a preset's own JSON to its scripts as SCRIPT_INPUT. It is always
			   defined -- empty when the preset declares none -- so a script reading a field of it
			   gets undefined rather than a ReferenceError. Evaluated as a JS literal, because JSON
			   is one, which keeps nested objects and arrays exactly as the pack wrote them. */
			Object scriptInput = null;
			if (preset.scriptInput != null) {
				try {
					scriptInput = cx.evaluateString(scope, "(" + preset.scriptInput + ")", "<scriptInput>", 1, null);
				} catch (Exception e) {
					Joban.LOGGER.warn("[Joban Client] PIDS preset \"" + preset.id
							+ "\" has a scriptInput that is not valid JSON; scripts see an empty object: " + e);
				}
			}
			if (scriptInput == null) {
				scriptInput = cx.newObject(scope);
			}
			scope.put("SCRIPT_INPUT", scope, scriptInput);

			/* JCM 2.x runs scriptTexts before scriptFiles, and a preset may consist of nothing else. */
			for (int textIndex = 0; textIndex < preset.scriptTexts.size(); textIndex++) {
				try {
					cx.evaluateString(scope, preset.scriptTexts.get(textIndex),
							"<scriptTexts[" + textIndex + "]>", 1, null);
					anyLoaded = true;
				} catch (Exception e) {
					Joban.LOGGER.error("[Joban Client] PIDS preset \"" + preset.id + "\" scriptTexts["
							+ textIndex + "] failed: " + e);
				}
			}

			for (String scriptFile : preset.scriptFiles) {
				if (evaluateResource(cx, scope, scriptFile)) {
					anyLoaded = true;
				}
			}
			if (!anyLoaded) {
				Joban.LOGGER.warn("[Joban Client] PIDS preset \"{}\" lists scripts but none could be read.", preset.id);
				return null;
			}

			return new Program(key, preset.id, preset.displayName(), pos, scope);
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
		/* Undelivered failure notices describe programs that no longer exist. */
		ERROR_NOTIFIER.reset();
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
		/* The shutter has to be in place before a single line of pack-provided script runs. */
		ScriptClassShutter.install(cx, SHUTTER);
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

	/**
	 * Puts the class shutter on a context.
	 *
	 * <p>Public because a background worker enters its own context on its own thread and must
	 * be under the same restrictions as the render thread.</p>
	 */
	static void installShutter(Context cx) {
		ScriptClassShutter.install(cx, SHUTTER);
	}

	private static void registerGlobals(Context cx, Scriptable scope) {
		ScriptableObject.putProperty(scope, "Resources", new NativeJavaClass(scope, Resources.class));
		ScriptableObject.putProperty(scope, "TextUtil", new NativeJavaClass(scope, TextUtil.class));
		ScriptableObject.putProperty(scope, "MinecraftClient", new NativeJavaClass(scope, MinecraftClient.class));
		/* A constructible class, not a bag of statics: scripts write new RateLimit(seconds). */
		ScriptableObject.putProperty(scope, "RateLimit", new NativeJavaClass(scope, RateLimit.class));
		ScriptableObject.putProperty(scope, "Text", new NativeJavaClass(scope, ScriptDrawCalls.Text.class));
		ScriptableObject.putProperty(scope, "Texture", new NativeJavaClass(scope, ScriptDrawCalls.Texture.class));
		ScriptableObject.putProperty(scope, "Rectangle", new NativeJavaClass(scope, ScriptDrawCalls.Rectangle.class));
		/* Math objects, documented under Common APIs and available to every script type. A pack that
		   builds a transformation stack reaches for Matrices without checking whether it exists. */
		ScriptableObject.putProperty(scope, "Matrices", new NativeJavaClass(scope, ScriptMath.Matrices.class));
		ScriptableObject.putProperty(scope, "Vector3f", new NativeJavaClass(scope, ScriptMath.Vector3f.class));
		/* Common-API utilities. Independently of one another: a preset may use a clock and never a
		   tracker, or the other way round. */
		ScriptableObject.putProperty(scope, "Timing", new NativeJavaClass(scope, ScriptTrackers.Timing.class));
		ScriptableObject.putProperty(scope, "StateTracker", new NativeJavaClass(scope, ScriptTrackers.StateTracker.class));
		ScriptableObject.putProperty(scope, "CycleTracker", new NativeJavaClass(scope, ScriptTrackers.CycleTracker.class));
		/* The two that let a preset do slow work without stalling a frame. 琼岭's pack calls
		   BackgroundWorker.submit at load time, so its absence is a black panel, not a slow one. */
		ScriptableObject.putProperty(scope, "BackgroundWorker", new NativeJavaClass(scope, ScriptNetwork.BackgroundWorker.class));
		ScriptableObject.putProperty(scope, "TickableSoundInstance", new NativeJavaClass(scope, ScriptSound.TickableSoundInstance.class));
		ScriptableObject.putProperty(scope, "Networking", new NativeJavaClass(scope, ScriptNetwork.Networking.class));
		/* Packs log through console.debug/warn/error, and the calls sit in catch blocks -- so a
		   missing console turns one failure into two and hides the first. */
		ScriptableObject.putProperty(scope, "console", new NativeJavaClass(scope, Console.class));

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

		/** {@code Resources.getMTRVersion()} -- the MTR version, as a string. */
		public static String getMTRVersion() {
			return com.jsblock.Joban.getMTRVersion();
		}

		/**
		 * {@code Resources.getAddonVersion(name)} -- the version of an addon, by mod id.
		 *
		 * <p>Only this mod answers: the packs that ask are checking whether JCM is new enough for
		 * something, and an empty string is the honest answer for anything else.</p>
		 */
		public static String getAddonVersion(String modId) {
			return modId != null && (modId.equalsIgnoreCase("jcm") || modId.equalsIgnoreCase("jsblock"))
					? com.jsblock.Joban.getVersion() : "";
		}
	}

	/** {@code TextUtil.cycleString("a|b")} — alternates the parts as the game ticks. */
	/** {@code console.log} and friends -- a pack's own logging, sent to the game log. */
	public static final class Console {
		private Console() {
		}

		public static void log(Object message) {
			com.jsblock.Joban.LOGGER.info("[PIDS script] {}", message);
		}

		public static void debug(Object message) {
			log(message);
		}
	
		public static void info(Object message) {
			log(message);
		}
	
		public static void warn(Object message) {
			com.jsblock.Joban.LOGGER.warn("[PIDS script] {}", message);
		}
	
		public static void error(Object message) {
			com.jsblock.Joban.LOGGER.error("[PIDS script] {}", message);
		}
	}

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

	/**
	 * {@code new RateLimit(seconds)} — throttles part of a script's work to a fixed interval.
	 *
	 * <p>Ported from JCM 2.x's {@code RateLimitJS}
	 * (fabric/src/main/java/com/lx862/mtrscripting/core/util/RateLimitJS.java), MIT License,
	 * Copyright (c) 2022-present Zbx1425. JCM 2.x measures against {@code TimingJS.globalElapsed};
	 * only differences of that clock are ever used, so a monotonic clock here is equivalent.
	 * It must be a real class rather than a holder of statics because presets construct it:
	 * {@code pids_ql_lite.js} calls {@code new RateLimit(...)} on its sixth line, and without it
	 * the whole preset failed to evaluate.</p>
	 */
	public static final class RateLimit {
		private double lastTime;
		private final double interval;

		public RateLimit(double interval) {
			this.interval = interval;
		}

		/** @return {@code true} at most once per interval, and always on the first call */
		public boolean shouldUpdate() {
			final double now = System.nanoTime() / 1e9;
			if (now - lastTime > interval) {
				lastTime = now;
				return true;
			}
			return false;
		}

		public void resetCoolDown() {
			lastTime = 0;
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

		/**
		 * {@code MinecraftClient.localPlayer()} -- the player, as a {@link PlayerInfo}.
		 *
		 * <p>Null on a dedicated server or before a world is joined, which is what the docs'
		 * nullable return means.</p>
		 */
		public static PlayerInfo localPlayer() {
			final Minecraft minecraft = Minecraft.getInstance();
			return minecraft == null || minecraft.player == null ? null : new PlayerInfo(minecraft.player);
		}

		/** {@code MinecraftClient.displayMessage(message, actionBar)}. */
		public static void displayMessage(Object message, boolean actionBar) {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.gui == null || message == null) {
				return;
			}
			final net.minecraft.network.chat.Component text =
					net.minecraft.network.chat.Component.literal(org.mozilla.javascript.Context.toString(message));
			if (actionBar) {
				minecraft.gui.setOverlayMessage(text, false);
			} else {
				minecraft.gui.getChat().addMessage(text);
			}
		}

		/** {@code MinecraftClient.narrate(message)} -- spoken by the narrator when one is on. */
		public static void narrate(Object message) {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || message == null) {
				return;
			}
			try {
				minecraft.getNarrator().sayNow(
						net.minecraft.network.chat.Component.literal(org.mozilla.javascript.Context.toString(message)));
			} catch (Throwable t) {
				com.jsblock.Joban.LOGGER.info("[PIDS script] narrate: {}", message);
			}
		}
		
		/** {@code MinecraftClient.renderDistance()}. */
		public static int renderDistance() {
			final Minecraft minecraft = Minecraft.getInstance();
			return minecraft == null || minecraft.options == null ? 0 : minecraft.options.renderDistance().get();
		}
		
		/** {@code MinecraftClient.gamePaused()}. */
		public static boolean gamePaused() {
			final Minecraft minecraft = Minecraft.getInstance();
			return minecraft != null && minecraft.isPaused();
		}

		/** {@code MinecraftClient.worldIsRainingAt(pos)}. */
		public static boolean worldIsRainingAt(Object pos) {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.level == null || !(pos instanceof ScriptMath.Vector3f)) {
				return false;
			}
			return minecraft.level.isRainingAt(((ScriptMath.Vector3f) pos).rawBlockPos());
		}
		
		/** {@code MinecraftClient.lightLevelAt(pos)}. */
		public static int lightLevelAt(Object pos) {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.level == null || !(pos instanceof ScriptMath.Vector3f)) {
				return 0;
			}
			return minecraft.level.getMaxLocalRawBrightness(((ScriptMath.Vector3f) pos).rawBlockPos());
		}
		
		/**
		 * {@code PlayerEntity} -- what {@code localPlayer()} hands back.
		 *
		 * <p>Positions are returned as {@link ScriptMath.Vector3f}, the same type
		 * {@code pids.blockPos()} and {@code stop} coordinates use, so a script can measure between
		 * them with the vector's own {@code distance} -- which is exactly how the 琼岭 pack decides
		 * whether anyone is near enough to make the panel worth drawing.</p>
		 */
		public static final class PlayerInfo {
			private final net.minecraft.world.entity.player.Player player;
		
			PlayerInfo(net.minecraft.world.entity.player.Player player) {
				this.player = player;
			}
		
			public String uuid() {
				return player.getUUID().toString();
			}
		
			public String name() {
				return player.getName().getString();
			}
		
			public ScriptMath.Vector3f pos() {
				return new ScriptMath.Vector3f(player.getX(), player.getY(), player.getZ());
			}
		
			public ScriptMath.Vector3f blockPos() {
				final net.minecraft.core.BlockPos pos = player.blockPosition();
				return new ScriptMath.Vector3f(pos.getX(), pos.getY(), pos.getZ());
			}
		
			/** The eye position, which is what a 'smooth' position means here. */
			public ScriptMath.Vector3f smoothPos() {
				return new ScriptMath.Vector3f(player.getX(), player.getEyeY(), player.getZ());
			}
		
			public ScriptMath.Vector3f velocity() {
				return new ScriptMath.Vector3f(player.getDeltaMovement().x, player.getDeltaMovement().y,
						player.getDeltaMovement().z);
			}
		
			public boolean hasPermissionLevel(int level) {
				return player.hasPermissions(level);
			}
		}
	}

	// ==================================================================
	// Program
	// ==================================================================

	/** A compiled preset script, ready to be asked to render frames. */
	public static final class Program {

		private final String key;
		private final String presetId;
		private final String displayName;
		private final net.minecraft.core.BlockPos blockPos;
		private final Scriptable scope;
		/** Per-program script state, the {@code state} argument JCM 2.x hands to the script. */
		private final ScriptableObject state;
		/** Distinct failures already reported, so a per-frame throw cannot flood the log. */
		private final Set<String> reportedErrors = new HashSet<>();
		/** Wall time of the last function call, for the debug overlay. */
		private volatile double lastExecutionMs;
		/** The most recent failure, or {@code null}; cleared by a call that succeeds. */
		private volatile String lastError;
		/** How many distinct draw-call sequences this program has already written to the log. */
		private static final int MAX_TRACES = 40;
		/** The last sequence written, so an unchanged panel produces no output at all. */
		private String lastTraceSignature;
		/** Wall time of the last trace written, used to rate-limit a panel that redraws often. */
		private long lastTraceMillis;
		/** Traces written so far, bounded by {@link #MAX_TRACES}. */
		private int traceCount;
		/** Set once the preset has thrown and been given the lenient arrivals wrapper. */
		private volatile boolean lenientArrivals;
		/** Set once the player has been told about the placeholder arrivals. */
		private volatile boolean lenientReported;
		/**
		 * The chat line a failed render queued, held until the caller knows whether the frame
		 * was recovered. See {@link #flushFailureNotice()} and {@link #discardFailureNotice()}.
		 */
		private volatile Runnable pendingFailureNotice;

		/**
		 * Sends the notice a failed render queued.
		 *
		 * <p>Called when the frame could not be recovered, so the player does need to know.</p>
		 */
		public void flushFailureNotice() {
			final Runnable notice = pendingFailureNotice;
			pendingFailureNotice = null;
			if (notice != null) {
				notice.run();
			}
		}

		/**
		 * Drops the notice a failed render queued, because the retry drew the frame after all.
		 *
		 * <p>The throw is still in the log — a preset author needs it — but the player watching
		 * a working board does not need to be told about it.</p>
		 */
		public void discardFailureNotice() {
			pendingFailureNotice = null;
		}

		Program(String key, String presetId, String displayName, net.minecraft.core.BlockPos blockPos, Scriptable scope) {
			this.key = key;
			this.presetId = presetId;
			this.displayName = displayName;
			this.blockPos = blockPos;
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

		/** @return the preset id this program was compiled for. */
		public String getPresetId() {
			return presetId;
		}

		/** @return the preset name shown to players, falling back to the id. */
		public String getDisplayName() {
			return displayName == null || displayName.isEmpty() ? presetId : displayName;
		}

		/** @return the block this program belongs to, or {@code null}. */
		public net.minecraft.core.BlockPos getBlockPos() {
			return blockPos;
		}

		/** @return milliseconds spent in the last {@code create}/{@code render}/{@code dispose} call. */
		public double getLastExecutionMs() {
			return lastExecutionMs;
		}

		/** @return the last failure message, or {@code null} when the last call succeeded. */
		/**
		 * The program's script scope.
		 *
		 * <p>Needed by anything that hands a JavaScript array back to a script: Rhino only
		 * gives an array its prototype when the array is made through a scope, and without it
		 * {@code .map()} on that array fails.</p>
		 */
		public Scriptable getScope() {
			return scope;
		}

		public String getLastError() {
			return lastError;
		}

		private boolean isDefined(String function) {
			return scope.get(function, scope) instanceof org.mozilla.javascript.Function;
		}

		private Object invoke(String function, ScriptRenderContext ctx, PIDSWrapper pids) {
			return invokeInternal(function, ctx, pids, false, false);
		}

		/** @return {@code true} when the function ran without throwing. */
		private boolean invokeChecked(String function, ScriptRenderContext ctx, PIDSWrapper pids) {
			return (Boolean) invokeInternal(function, ctx, pids, true, true);
		}

		/**
		 * @param deferPlayerNotice hold the chat line back instead of sending it, for a failure
		 *                          the caller may still recover from by retrying the frame; see
		 *                          {@link #flushFailureNotice()} and {@link #discardFailureNotice()}
		 */
		private Object invokeInternal(String function, ScriptRenderContext ctx, PIDSWrapper pids,
									  boolean returnSuccess, boolean deferPlayerNotice) {
			if (!isDefined(function)) {
				return returnSuccess ? Boolean.TRUE : Undefined.instance;
			}
			final Context cx = Context.enter();
			final long started = System.nanoTime();
			try {
				cx.setLanguageVersion(Context.VERSION_ES6);
				cx.setOptimizationLevel(-1);
				ScriptClassShutter.install(cx, SHUTTER);
				final Object fn = scope.get(function, scope);
				/* Always three arguments, so a script that ignores ctx (as create/dispose
				   usually do) still receives its state and pids in the right positions. */
				final Object[] args = new Object[]{ctx, state, pids};
				/* Arrays the wrapper hands back are built through this scope, and Rhino only
				   gives an array its prototype when it is created that way. */
				PIDSWrapper.enterScriptScope(scope);
				final Object result;
				try {
					result = ((org.mozilla.javascript.Function) fn).call(cx, scope, scope, args);
				} finally {
					PIDSWrapper.exitScriptScope();
				}
				lastExecutionMs = (System.nanoTime() - started) / 1_000_000.0;
				lastError = null;
				return returnSuccess ? Boolean.TRUE : result;
			} catch (Exception e) {
				/* A script that throws keeps throwing every frame -- the earlier build wrote
				   ~8 MB of log in a few seconds this way. Report the first occurrence of each
				   distinct failure with enough context to act on it, then stay quiet until the
				   next resource reload recompiles the program. */
				lastExecutionMs = (System.nanoTime() - started) / 1_000_000.0;
				final String signature = function + "|" + e.getClass().getSimpleName() + "|" + e.getMessage();
				lastError = function + "(): " + e.getMessage();
				if (reportedErrors.add(signature)) {
					Joban.LOGGER.error("[Joban Client] PIDS script \"{}\" threw in {}(): {}", key, function, e.toString());
					/* Where in the script, and by which call. The message alone names the line the
					   statement starts on, which is not the same as the expression that failed --
					   a map() callback on the end of a long chained statement reports the statement's
					   first line, not the callback's. */
					if (e instanceof org.mozilla.javascript.RhinoException) {
						final String stack = ((org.mozilla.javascript.RhinoException) e).getScriptStackTrace();
						if (stack != null && !stack.isEmpty()) {
							Joban.LOGGER.error("[Joban Client]   JS stack: {}", stack.replace('\n', ' '));
						}
					}
					if (pids != null) {
						Joban.LOGGER.error("[Joban Client]   arrivals available: {}, rows: {}, preset: {}"
										+ " -- a script that indexes past the end gets null, and anything after the"
										+ " throw (including its background) is never drawn",
								pids.arrivals().size(), pids.rows, pids.type);
					}
					Joban.LOGGER.error("[Joban Client]   (repeated failures of this kind are suppressed until reload)");
					final Runnable notice = () -> notifyPlayer(function, e);
					if (deferPlayerNotice) {
						/* The frame may still be drawn by the retry, and a red line about a panel
						   that then paints itself is worse than no line at all. */
						pendingFailureNotice = notice;
					} else {
						notice.run();
					}
				}
				return returnSuccess ? Boolean.FALSE : Undefined.instance;
			} finally {
				Context.exit();
			}
		}

		/**
		 * Queues a chat line telling the player this preset is broken.
		 *
		 * <p>JCM 2.x only does this in debug mode; here it is its own config switch, on by
		 * default, because a PIDS panel that stays black is otherwise indistinguishable from
		 * a preset that simply draws nothing. The message names the preset and the function,
		 * not the stack trace -- the log has that.</p>
		 */
		private void notifyPlayer(String function, Exception e) {
			if (!com.jsblock.client.ClientConfig.isScriptErrorNotificationEnabled()) {
				return;
			}
			final String preset = displayName == null ? presetId : displayName;
			final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			ERROR_NOTIFIER.queue(() -> {
				final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
				if (minecraft == null || minecraft.player == null) {
					return;
				}
				minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
						"\u00a7c[Joban Client] PIDS \u00a7f" + preset + " \u00a7cthrew in " + function + "(): " + message), false);
			});
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
			final boolean success = invokeChecked("render", ctx, pids);
			reportTrace(ctx, pids);
			return success;
		}

		/** @return whether this program is currently handed the lenient arrivals wrapper. */
		public boolean usesLenientArrivals() {
			return lenientArrivals;
		}

		/**
		 * Switches this program to the lenient arrivals wrapper, the first time only.
		 *
		 * <p>Called after a script threw while reading an arrival, so that the frame can be
		 * retried with {@code arrivals().get(i)} handing out a placeholder instead of
		 * {@code null}. The usual cause is a preset that indexes past the end without checking
		 * — the CRT pack reaches for the second train on a platform that has one — which JCM 2.x
		 * breaks on too. A resource pack cannot be fixed from here, so the choice is between a
		 * board that shows something and a board that shows nothing, and this takes the first.
		 * Guarded presets never reach this point, so they keep the strict behaviour that leaves
		 * their empty rows blank.</p>
		 *
		 * <p>Nothing is reported here: the caller does not yet know whether the retry draws the
		 * frame, and this wrapper only helps when the null was an arrival. The report is
		 * {@link #reportLenientFallback()}, sent once the retry has actually worked.</p>
		 *
		 * @return {@code true} for the call that switched it, so the caller retries once and
		 * not once per frame
		 */
		public boolean adoptLenientArrivals() {
			synchronized (this) {
				if (lenientArrivals) {
					return false;
				}
				lenientArrivals = true;
			}
			return true;
		}

		/**
		 * Says, once, that this preset is being drawn with the placeholder arrivals.
		 *
		 * <p>The throw is already in the log; this is the explanation for it, and the only line
		 * the player sees — so it is sent when the retry has <em>worked</em>, and never for a
		 * panel that is showing its background because the retry failed as well.</p>
		 */
		public void reportLenientFallback() {
			if (lenientReported) {
				return;
			}
			lenientReported = true;
			Joban.LOGGER.warn("[Joban Client] PIDS preset \"{}\" reads arrivals().get(i) past the end of the"
					+ " list without checking for null, which is what JCM 2.x returns there. It is now drawn"
					+ " with a placeholder arrival, so its empty rows may show wording meant for an empty"
					+ " train. Fix the preset to test for null.", key);
			ERROR_NOTIFIER.queue(() -> {
				final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
				if (minecraft == null || minecraft.player == null) {
					return;
				}
				minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
						"\u00a7e[Joban Client] \u00a7f" + getDisplayName()
								+ " \u00a7easks for more trains than the platform has and does not check for it."
								+ " Showing it with placeholder rows."), false);
			});
		}

		/**
		 * Writes this frame's draw calls to the log whenever the sequence changes.
		 *
		 * <p>Answers the questions a screenshot cannot: whether a script reached the branch
		 * that should have drawn something, and whether two calls that look like one are
		 * fighting over the same depth. A panel that alternates between two texts shows up
		 * here as two call lists that differ in exactly that text, which separates a
		 * deliberate {@code cycleString} from genuine depth fighting.</p>
		 *
		 * <p>Only on change, and at most once a second: a preset redraws every frame, and a
		 * marquee or a clock changes the sequence often enough to flood the log otherwise.</p>
		 */
		private void reportTrace(ScriptRenderContext ctx, PIDSWrapper pids) {
			if (ctx == null || !ctx.isTracing()) {
				return;
			}
			final List<String> calls = ctx.traceCalls();
			final StringBuilder joined = new StringBuilder();
			for (String call : calls) {
				joined.append(call).append('\n');
			}
			final String signature = joined.toString();
			if (signature.equals(lastTraceSignature)) {
				return;
			}
			lastTraceSignature = signature;
			final long now = System.currentTimeMillis();
			if (now - lastTraceMillis < 1000L) {
				return;
			}
			lastTraceMillis = now;
			traceCount++;
			if (traceCount > MAX_TRACES) {
				if (traceCount == MAX_TRACES + 1) {
					Joban.LOGGER.info("[Joban Client] [PIDS trace] {}: further changes are not logged.", key);
				}
				return;
			}
			final StringBuilder report = new StringBuilder();
			report.append("[Joban Client] [PIDS trace] ").append(key)
					.append(" gameTick=").append(traceTick())
					.append(" zOrderStep=").append(ScriptRenderContext.Z_ORDER_STEP)
					.append(" cards=").append(pids == null ? 0 : pids.arrivals().size())
					.append(" calls=").append(calls.size());
			for (int i = 0; i < calls.size(); i++) {
				report.append("\n    ").append(i).append(": ").append(calls.get(i));
			}
			Joban.LOGGER.info(report.toString());
		}

		/** @return MTR's game tick, or the wall-clock equivalent outside a game. */
		private static long traceTick() {
			try {
				return (long) mtr.MTRClient.getGameTick();
			} catch (Throwable t) {
				return System.currentTimeMillis() / 50L;
			}
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
