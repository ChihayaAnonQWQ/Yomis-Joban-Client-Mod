package com.jsblock.script;

import com.jsblock.Joban;
import com.jsblock.data.PIDSPreset;
import mtr.MTRClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
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
		/* A script cannot be told it is being reloaded, so the canvases it made are released here --
		   before the texture bookkeeping is cleared, since each canvas unregisters its own identifier
		   on the way out. */
		GraphicsTexture.releaseAll();
		/* Script textures are keyed by the identifiers the old pack used, so they must go too. */
		ScriptTextures.reset();
		/* The fonts are read out of the packs that are being replaced. */
		Resources.resetFonts();
		/* Which refusals have been reported describes scripts that no longer exist, and the
		   canvases those scripts made are released with them. */
		ScriptPaths.reset();
		/* Undelivered failure notices describe programs that no longer exist. */
		ERROR_NOTIFIER.reset();
	}

	/**
	 * Builds a Rhino scope with the PIDS globals registered.
	 *
	 * <p>Exposed so the headless script check can compile a real preset script through exactly
	 * the same globals the game registers, and therefore fail on the same missing-API mistakes
	 * a player would hit.</p>
	 *
	 * <h2>The scope is an {@code ImporterTopLevel}, not {@code initStandardObjects()}</h2>
	 * <p>JCM 2.x builds its script scope with Rhino's <b>importer</b> top level. Its bytecode says so
	 * outright: {@code ParsedScript.<init>} is
	 * {@code invokestatic Context.enter}, {@code setLanguageVersion(200)}, {@code setClassShutter(...)},
	 * then {@code new ImporterTopLevel} / {@code invokespecial ImporterTopLevel.<init>(Context)} and
	 * {@code putfield scope} — javap, this branch's reference jar. That global is what a pack's first
	 * two lines reach for:</p>
	 *
	 * <pre>{@code
	 * importPackage(java.awt);
	 * importPackage(java.awt.geom);
	 * }</pre>
	 *
	 * <p>{@code initStandardObjects()} answers a scope of pure <em>standard</em> objects — ECMAScript
	 * built-ins and nothing else. It has no {@code importPackage}, no {@code importClass}, no
	 * {@code Packages} and none of the {@code java} / {@code javax} / {@code org} / {@code com} /
	 * {@code edu} / {@code net} package roots, so a script that begins with an import dies of a
	 * {@code ReferenceError} <b>while it is being loaded</b> — before {@code create()} exists, and
	 * therefore before anything is drawn. That is the whole of the HKR Split-Flap Display preset's
	 * "the panel is transparent" report: see {@code next_train.js#27}.</p>
	 *
	 * <p>{@code ImporterTopLevel.initStandardObjects(cx, false)} is the same call
	 * {@code Context.initStandardObjects()} makes internally — javap shows it as
	 * {@code Context.initStandardObjects(ScriptableObject, Z)} followed by
	 * {@code exportAsJSClass(3, this, false)} and {@code delete("constructor")} — so this is that
	 * scope plus the importer, and not a different set of built-ins. The importer is inert until a
	 * script calls it: {@code importPackage} only records the package on the scope, and resolution
	 * happens later, per name.</p>
	 *
	 * <h2>The importer does not open a hole in the sandbox</h2>
	 * <p>The importer resolves a name through {@code ImporterTopLevel.get} →
	 * {@code NativeJavaPackage.getPkgProperty}, which calls
	 * {@code Context.getClassShutter().visibleToScripts(fullName)} <b>before</b> it loads the class —
	 * javap of {@code org.mozilla.javascript.NativeJavaPackage} shows the shutter read and the
	 * branch that follows it, four instructions ahead of {@code Kit.classOrNull}. So
	 * {@code java.lang.System} resolves to a package rather than a class, and
	 * {@code importClass(java.lang.System)} is refused outright. {@link ScriptClassShutter} stays
	 * the single gate, which is why this port needs no importer of its own; {@code ScriptShutterCheck}
	 * asserts it from the script side, and the allow-list is unchanged.</p>
	 */
	public static Scriptable newScope(Context cx) {
		cx.setLanguageVersion(Context.VERSION_ES6);
		cx.setOptimizationLevel(-1); // Rhino's interpreter: avoids class generation issues on a mod classloader.
		/* The shutter has to be in place before a single line of pack-provided script runs, and
		   before the importer can resolve anything: NativeJavaPackage reads it off the context at
		   resolution time. This is also JCM 2.x's own order -- setClassShutter, then the scope. */
		ScriptClassShutter.install(cx, SHUTTER);
		final Scriptable scope = new ImporterTopLevel(cx);
		registerGlobals(cx, scope);
		return scope;
	}

	// ==================================================================
	// Reference spelling
	// ==================================================================

	/**
	 * The resource locations worth trying for one script reference, best first.
	 *
	 * <h2>Why this is not just {@code new ResourceLocation(reference)}</h2>
	 * <p>Minecraft resource paths are lower case: {@code ResourceLocation} refuses a capital
	 * letter outright, and its constructor throws rather than answering {@code null}. Resource
	 * packs are written by hand, though, and a pack that names its files
	 * {@code scripts/Digital_Rail.js} — met transit ships exactly that name — spells them with
	 * capitals when it lists {@code scriptFiles}. In v2 on MTR 4 that preset loads because the
	 * whole path is lower-cased before the read; here it used to fail at the constructor and the
	 * preset drew nothing.</p>
	 *
	 * <p>The candidates are therefore:</p>
	 * <ol>
	 *   <li>the reference as written, which is what a well-formed pack gets and the only
	 *       candidate that is tried for one;</li>
	 *   <li>its lower-case form, which is what the game's own resource manager is indexed by, so
	 *       {@code Digital_Rail.js} resolves against a pack that stored it lower case;</li>
	 *   <li>every resource in that namespace's {@code scripts/} directory whose lower-case path
	 *       matches — the fall-back for a pack that stored the file with capitals, where the
	 *       resource manager's index has no lower-case entry to find.</li>
	 * </ol>
	 *
	 * <p>The third step is the one that needs the resource manager to enumerate rather than look
	 * up, so it is done with a single filtered walk and only when the first two miss. It is
	 * bounded by the number of scripts the installed packs ship.</p>
	 *
	 * <p><b>This is tolerance for packs, not Minecraft's rule.</b> The game, the resource manager
	 * and every other reader in it remain case sensitive; a pack that relies on this is relying
	 * on something only its own scripts do. The warning says so at the point of use, so an author
	 * who sees it knows the name is the problem.</p>
	 *
	 * @param reference the {@code namespace:path} a script wrote
	 * @return the locations to try, in order; never empty
	 * @throws ScriptPaths.RejectedPathException when the reference could leave its own pack
	 */
	static java.util.List<ResourceLocation> caseFoldedCandidates(String reference) {
		final java.util.List<ResourceLocation> candidates = new java.util.ArrayList<>(3);

		final ResourceLocation exact = ScriptPaths.resourceOrNull(reference);
		if (exact != null) {
			candidates.add(exact);
			return candidates;
		}

		/* The reference is refused only for its spelling, so the path rules have already run and
		   the lower-case form is safe to build. */
		final ResourceLocation folded = ScriptPaths.foldToResourceLocation(reference);
		candidates.add(folded);
		candidates.addAll(caseInsensitiveScripts(folded));
		return candidates;
	}

	/**
	 * Walks the {@code scripts/} directory of a namespace for a file whose lower-case path is the
	 * one asked for.
	 *
	 * <p>Only reached for a reference that has capitals in it, so the walk costs a resource
	 * enumeration on a handful of presets rather than on every script. A pack that stored the file
	 * with capitals is the case this exists for, and it is also the case where the returned
	 * location — built from a path the resource manager itself reported — is the one that reads
	 * back successfully, capitals and all.</p>
	 *
	 * @return matching locations, most specific first; empty when there are none
	 */
	private static java.util.List<ResourceLocation> caseInsensitiveScripts(ResourceLocation wanted) {
		final java.util.List<ResourceLocation> found = new java.util.ArrayList<>();
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return found;
		}
		try {
			/* "scripts" rather than the full path: the resource manager matches a directory prefix,
			   so one walk covers every script in the namespace regardless of how deeply a pack
			   nests them. It answers a map keyed by location, so the keys are what is wanted. */
			for (ResourceLocation path : minecraft.getResourceManager()
					.listResources("scripts", path -> path.getNamespace().equals(wanted.getNamespace()))
					.keySet()) {
				if (path.getPath().equalsIgnoreCase(wanted.getPath())) {
					found.add(path);
				}
			}
		} catch (Exception e) {
			Joban.LOGGER.debug("[Joban Client] Could not enumerate scripts under {}: {}",
					wanted.getNamespace(), e.toString());
		}
		/* A pack may ship the same script twice with different capitals. Shortest first, so the
		   plainest spelling wins and the choice is at least deterministic. */
		found.sort(java.util.Comparator.comparingInt(location -> location.getPath().length()));
		return found;
	}

	/**
	 * Reads and evaluates a script from the client resource manager.
	 *
	 * @param location a resource location as the script wrote it, e.g.
	 *                 {@code jsblock:scripts/pids_util.js}
	 * @return {@code true} when the script existed and evaluated
	 */
	static boolean evaluateResource(Context cx, Scriptable scope, String location) {
		if (location == null || location.isEmpty() || !INCLUDE_STACK.add(location)) {
			return false;
		}
		try {
			/* Before anything is resolved or read: a script may only reach its own pack. The
			   reference goes through the same validation the texture path uses, so
			   include("jsblock:../../../../x") is refused here rather than handed to a resource
			   manager that would resolve it against the pack's directory. See ScriptPaths. */
			final java.util.List<ResourceLocation> candidates = caseFoldedCandidates(location);
			String source = null;
			ResourceLocation used = null;
			String usedAssetPath = null;
			for (ResourceLocation candidate : candidates) {
				source = readResource(candidate);
				if (source != null && !source.trim().isEmpty()) {
					used = candidate;
					break;
				}
				source = null;
			}
			if (used == null) {
				/* The resource manager has nothing under any spelling of the name. That is the
				   expected outcome for a file stored with capitals: it is indexed under a
				   lower-cased name that is not its own, so no location can reach it. The packs
				   themselves are asked next, and they can still answer -- see ScriptPackFiles. */
				final String[] assetPath = new String[1];
				source = ScriptPackFiles.read(locationNamespace(location), locationAfterColon(location),
						resolved -> assetPath[0] = resolved);
				if (source != null && source.trim().isEmpty()) {
					source = null;
				} else {
					usedAssetPath = assetPath[0];
				}
			}
			if (source == null) {
				final boolean capitalised = !locationAfterColon(location)
						.equals(locationAfterColon(location).toLowerCase(java.util.Locale.ROOT));
				Joban.LOGGER.warn("[Joban Client] PIDS script {} is missing or empty"
						+ (capitalised ? " (searched the packs too, because the name has capitals in it)"
								: ""), location);
				return false;
			}
			/* The name the script is compiled under. A pack may spell the path differently from the
			   reference, and the name that reaches a stack trace should be the one on disk. */
			final String resolved = used != null ? used.toString()
					: usedAssetPath != null ? locationNamespace(location) + ":" + usedAssetPath : location;
			if (used != null && (!used.getPath().equals(locationAfterColon(location))
					|| !used.getNamespace().equals(locationNamespace(location)))) {
				Joban.LOGGER.warn("[Joban Client] PIDS script \"{}\" resolved as {}; a resource name"
						+ " with capitals in it is tolerated here but is not Minecraft's rule.",
						location, used);
			}
			cx.evaluateString(scope, source, resolved, 1, null);
			return true;
		} catch (ScriptPaths.RejectedPathException refused) {
			/* A refusal is not a broken pack: the script asked for something it may not have, so
			   the read is dropped and the rest of the preset carries on. Reported once per
			   distinct reference -- an include() sits in a path that can run every frame. */
			ScriptPaths.report("include()", refused);
			return false;
		} catch (Exception e) {
			Joban.LOGGER.error("[Joban Client] Error evaluating PIDS script " + location + ": " + e);
			return false;
		} finally {
			INCLUDE_STACK.remove(location);
		}
	}

	/** The path half of a {@code namespace:path} reference, as written. */
	private static String locationAfterColon(String reference) {
		final int colon = reference.indexOf(':');
		return colon < 0 ? reference : reference.substring(colon + 1);
	}

	/** The namespace half of a {@code namespace:path} reference, defaulting as Resources.id does. */
	private static String locationNamespace(String reference) {
		final int colon = reference.indexOf(':');
		return colon < 0 ? "minecraft" : reference.substring(0, colon);
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
		try (InputStream stream = minecraft.getResourceManager().getResource(id).orElseThrow().open()) {
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
		/* JCM 2.x registers this second name for the PIDS scope, and it does <b>not</b> point at the
		   Minecraft client helper above. Its bytecode loads a different class for it:
		   {@code JCMScripting.lambda$register$0} does {@code ldc class mtr/client/ClientData}, so a
		   v2 PIDS script reaching for {@code MTRClientData.STATIONS} or {@code MTRClientData.PLATFORMS}
		   is reading MTR's own client caches. Wiring the name to our MinecraftClient instead would
		   leave exactly those scripts with {@code undefined} -- the pack would run and show nothing --
		   so it is the MTR class that goes here. Both classes exist in MTR 3.6.3 unchanged (the
		   arrival lists and station map the built-in PIDS renderers already read), so this is the same
		   object a v2 pack asked for, on this branch's MTR. */
		ScriptableObject.putProperty(scope, "MTRClientData", new NativeJavaClass(scope, mtr.client.ClientData.class));
		/* A constructible class, not a bag of statics: scripts write new RateLimit(seconds). */
		ScriptableObject.putProperty(scope, "RateLimit", new NativeJavaClass(scope, RateLimit.class));
		ScriptableObject.putProperty(scope, "Text", new NativeJavaClass(scope, ScriptDrawCalls.Text.class));
		ScriptableObject.putProperty(scope, "Texture", new NativeJavaClass(scope, ScriptDrawCalls.Texture.class));
		ScriptableObject.putProperty(scope, "Rectangle", new NativeJavaClass(scope, ScriptDrawCalls.Rectangle.class));
		/* A canvas a script draws into at runtime and then uses as a texture. JCM 2.x has it as a
		   global of the same name and shape; see GraphicsTexture for what this port changed (the GPU
		   side is built on the first upload, so it is usable with no game running) and why. */
		ScriptableObject.putProperty(scope, "GraphicsTexture", new NativeJavaClass(scope, GraphicsTexture.class));
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
		/* Persistent state between sessions. met transit's running board is the preset that
		   needs it: it logs which services have already departed and writes the log back, and
		   without the global its first line throws. Same four methods and the same two
		   directories as v2's FilesUtil; see FilesUtil for the path guard. */
		ScriptableObject.putProperty(scope, "Files", new NativeJavaClass(scope, FilesUtil.class));
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

		/**
		 * {@code Resources.readBufferedImage(id)} -- a pack image as an ARGB {@code BufferedImage}, or
		 * {@code null}.
		 *
		 * <p>JCM 2.x's own method, and the one an image-composing script needs: it is how a canvas gets
		 * the pack's artwork into it (see {@link GraphicsTexture#drawTexture}), and how a script reads
		 * an image at all without a texture. Anything from {@code Resources.id(...)} or a
		 * {@code "namespace:path.png"} string is accepted, which is the same spelling the rest of the
		 * API uses.</p>
		 *
		 * <p>Read through the script path rules like every other read, and a missing file answers
		 * {@code null} with one warning rather than throwing: a pack image that is not there is
		 * normally an element the script can leave out.</p>
		 */
		public static java.awt.image.BufferedImage readBufferedImage(Object id) {
			final ResourceLocation location;
			if (id instanceof ResourceLocation) {
				location = (ResourceLocation) id;
			} else if (id == null) {
				return null;
			} else {
				try {
					location = ScriptPaths.resource(org.mozilla.javascript.Context.toString(id));
				} catch (ScriptPaths.RejectedPathException refused) {
					ScriptPaths.report("Resources.readBufferedImage()", refused);
					return null;
				}
			}
			return ScriptTextures.readImage(location);
		}

		/**
		 * {@code Resources.getSystemFont(name)} — an AWT {@code Font} for a name JCM knows.
		 *
		 * <h2>The two names, from the reference jar</h2>
		 * <p>{@code javap -p -c com.lx862.mtrscripting.util.ScriptResourceUtil} gives the whole
		 * method, and it is three branches:</p>
		 * <ul>
		 *   <li>{@code "Noto Sans"} → a cached {@code Font.createFont} of
		 *       {@code mtr:font/noto-sans-cjk-tc-medium.otf} when the class's
		 *       {@code hasNotoSansCjk} flag is set, otherwise of
		 *       {@code mtr:font/noto-sans-semibold.ttf}. A read failure logs
		 *       {@code "[Scripting] Failed to load font"} and leaves the cache {@code null}, so the
		 *       method answers {@code null}.</li>
		 *   <li>{@code "Noto Serif"} → a cached {@code Font.createFont} of
		 *       {@code mtr:font/noto-serif-cjk-tc-semibold.ttf}; a failure logs
		 *       {@code "[Scripting] Failed loading font"} and answers {@code null} immediately.</li>
		 *   <li><b>anything else</b> → {@code new Font(name, Font.PLAIN, 1)}. Note the size: the
		 *       caller is expected to scale it, and every pack does — HKR's {@code mk()} is
		 *       {@code base.deriveFont(style, size * T.k)}.</li>
		 * </ul>
		 *
		 * <p>The {@code hasNotoSansCjk} branch is <b>not</b> reproduced, and that is a deliberate
		 * reading of the reference rather than an omission: {@code javap -p -c} shows the field
		 * assigned in exactly one place in the whole class, its static initialiser, and the value
		 * there is {@code iconst_0} — it is {@code false} for the life of the process, so the
		 * {@code .otf} branch is unreachable in the jar this port is measured against. MTR 3.6.3
		 * does not ship that file either (listing {@code libs/mtr-common.jar}: the {@code mtr/font/}
		 * directory holds {@code mtr.json}, {@code noto-sans-semibold.ttf} and
		 * {@code noto-serif-cjk-tc-semibold.ttf} and nothing else), so a port that took the branch
		 * would only ever warn. The observable behaviour is identical.</p>
		 *
		 * <h2>What a pack actually asks for</h2>
		 * <p>The names the packs write are <em>not</em> these two. HKR asks for
		 * {@code "Noto Sans CJK TC Medium"}, {@code "Noto Sans SemiBold"} and {@code "Monospaced"},
		 * and the first two fall through to {@code new Font(name, PLAIN, 1)} — a lookup in the JVM's
		 * font registry. A name that is not in that registry is <b>silently replaced</b>, which is
		 * the failure this port fixes: measured on this machine's Java 21 (Azul),
		 * {@code new Font("Noto Sans SemiBold", PLAIN, 1).getFamily()} and the same call for
		 * {@code "Noto Sans CJK TC Medium"} both answer {@code "Dialog"} with
		 * {@code getFontName() == "Dialog.plain"}, and the only installed families holding a Noto
		 * name are {@code Noto Sans SC}, {@code Noto Sans SC Medium} and {@code Noto Serif SC}. The
		 * panel therefore drew in the JVM's substituted face rather than in the Noto the pack's
		 * author asked for — and JCM 2.x behaves the same way on a machine without those fonts, so
		 * this is a deliberate deviation, recorded in {@code MTR3-PIDS-PORT.md}.</p>
		 *
		 * <p>The lookup stays the <b>first</b> choice: a name this machine has is returned exactly
		 * as before, so no machine that already drew the pack correctly changes. Only a name the
		 * registry does not have is served from MTR's own bundled Noto instead of from AWT's
		 * substitution.</p>
		 *
		 * <h2>Deciding that a name is not installed</h2>
		 * <p>Two rules, and both are needed:</p>
		 * <ol>
		 *   <li>a one-shot, case-insensitive set of the installed family names
		 *       ({@code GraphicsEnvironment.getAvailableFontFamilyNames()}, default locale and
		 *       {@link java.util.Locale#ENGLISH} as well). The default-locale table alone is not
		 *       enough: on this {@code zh_CN} machine {@code "Microsoft YaHei"} is absent from it
		 *       while {@code new Font("Microsoft YaHei", ...).getFamily()} answers {@code "微软雅黑"}
		 *       — an installed font would have been mistaken for a missing one.</li>
		 *   <li>the resolved {@code Font}'s own family and face names, compared ignoring case, in
		 *       both locales. AWT resolves a <em>face</em> spelling like {@code "Arial Bold"} to
		 *       family {@code Arial} + face {@code "Arial Bold"}, and that spelling is in no family
		 *       table. Comparing {@code getName()} here would prove nothing — it returns the string
		 *       the caller passed in.</li>
		 * </ol>
		 * <p>A name that resolved to {@code Dialog} fails both rules, which is exactly the
		 * substitution signature.</p>
		 *
		 * <h2>Which bundled face, and when a CJK name keeps the machine's own font instead</h2>
		 * <p>Three answers, in this order — {@link #substituteFor}, which is package-private on
		 * purpose so the check can assert the branch a given machine does <em>not</em> take:</p>
		 * <ol>
		 *   <li>a name that <em>marks</em> a CJK script ({@code CJK}, {@code SC}, {@code TC},
		 *       {@code JP}, {@code KR}, {@code Hans}, {@code Hant}, … as a word, or a name written in
		 *       CJK/kana/hangul) is measured rather than assumed: the JVM's substitution is probed
		 *       for every script the name marks — {@code canDisplay('下')} for Han, and additionally
		 *       {@code 'あ'} for a Japanese mark and {@code '한'} for a Korean one, because a CJK
		 *       face carries Han whatever else it carries. <b>If it draws them all, it is kept.</b>
		 *       A machine with a real Chinese font installed already has a better answer than MTR's
		 *       bundled serif CJK file: HKR's {@code "Noto Sans CJK TC Medium"} is asking for a
		 *       <em>sans</em>, and the substitution may well be one.</li>
		 *   <li>a name that marks a CJK script the substitution <b>cannot</b> draw → MTR's bundled
		 *       CJK face ({@code mtr:font/noto-serif-cjk-tc-semibold.ttf}), the only bundled file
		 *       with CJK glyphs at all. This is the one place the port goes beyond "serif means
		 *       serif", and the reason is measured: the bundled Latin face has no CJK glyph, and
		 *       Java2D does <b>not</b> substitute a face per glyph for a physical font —
		 *       {@code drawString("下")} through {@code noto-sans-semibold.ttf} paints the same 246
		 *       pixels as an unmapped character, a missing-glyph box, while a logical font such as
		 *       {@code Dialog} paints the real character. The first version of this rule — "a CJK
		 *       name takes the bundled CJK file, unconditionally" — was wrong in the half that
		 *       matters, and is recorded in {@code MTR3-PIDS-PORT.md} §4.19: it discarded a
		 *       substitution that already drew the script, and it read per-glyph fallback as if it
		 *       worked for a {@code Font.createFont} face.</li>
		 *   <li>anything else → the bundled face the name's style asks for: {@code "serif"} in the
		 *       name selects {@code Noto Serif} (that same CJK file) and every other name the bundled
		 *       {@code Noto Sans} ({@code mtr:font/noto-sans-semibold.ttf}), which is the face HKR's
		 *       {@code "Noto Sans SemiBold"} asks for.</li>
		 * </ol>
		 *
		 * <p>Every resolved name is cached — the two built-ins in the reference implementation's
		 * plain static fields, and the whole branch-3 answer in
		 * {@link #resolvedByRequest} — so the per-frame call a renderer makes is a map lookup.
		 * {@code volatile} and the lock are there so a {@code BackgroundWorker} thread cannot race
		 * the render thread into loading the same 515 KB file twice; the value cached is the same
		 * one. A bundled read that fails answers the JVM's substituted font rather than throwing,
		 * and one INFO line per name names the request, what AWT did with it, what it is answered
		 * with and why.</p>
		 */
		public static java.awt.Font getSystemFont(String name) {
			if (name == null) {
				return null;
			}
			if (name.equals("Noto Sans")) {
				if (notoSans == null) {
					synchronized (Resources.class) {
						if (notoSans == null) {
							notoSans = ScriptFonts.read(NOTO_SANS);
						}
					}
				}
				return notoSans;
			}
			if (name.equals("Noto Serif")) {
				if (notoSerif == null) {
					synchronized (Resources.class) {
						if (notoSerif == null) {
							notoSerif = ScriptFonts.read(NOTO_SERIF);
						}
					}
				}
				return notoSerif;
			}
			/* Branch 3: the JVM's own font registry first, and only a name it does not have is
			   answered from MTR's bundled Noto. See the method comment for both halves of the
			   "is it installed" test and for how the answer is chosen. */
			java.awt.Font resolved = resolvedByRequest.get(name);
			if (resolved != null) {
				return resolved;
			}
			synchronized (Resources.class) {
				resolved = resolvedByRequest.get(name);
				if (resolved != null) {
					return resolved;
				}
				final java.awt.Font system = new java.awt.Font(name, java.awt.Font.PLAIN, 1);
				if (isInstalledFamily(name, system)) {
					resolvedByRequest.put(name, system);
					return system;
				}
				final Substitute chosen = substituteFor(system, name);
				resolvedByRequest.put(name, chosen.font);
				/* One line per request name -- the cache above is what makes it once, not a guard
				   here -- naming the request, what AWT did with it, what it is answered with and
				   why, so a panel in the wrong typeface can be traced from the log. */
				if (chosen.font == system) {
					Joban.LOGGER.info("[Joban Client] PIDS script font \"{}\" is not installed on this machine,"
							+ " so AWT substitutes \"{}\"; keeping the substitution, because {}",
							name, system.getFamily(), chosen.reason);
				} else {
					Joban.LOGGER.info("[Joban Client] PIDS script font \"{}\" is not installed on this machine"
							+ " (AWT silently substitutes \"{}\"); using \"{}\" instead, because {}",
							name, system.getFamily(), chosen.font.getFontName(), chosen.reason);
				}
				return chosen.font;
			}
		}

		/**
		 * Whether this machine's font registry has {@code name} — the guard that keeps the fallback
		 * off every font that is already there.
		 *
		 * <p>Rule one is the cached family table; rule two is the resolved font's own family and
		 * face names in both the default locale and English. Both are needed and for opposite
		 * reasons: the table has no <em>face</em> spelling in it ({@code "Arial Bold"} resolves to
		 * family {@code Arial}, and only {@code getFontName()} shows it is installed), while
		 * {@code getFamily()} is localized ({@code "Microsoft YaHei"} answers {@code "微软雅黑"}
		 * on this {@code zh_CN} machine, where the English name is the one in the table).
		 * {@code Font.getName()} is deliberately not consulted: it returns the string that was
		 * passed in, so it is equal to the request for every name including a missing one.</p>
		 */
		private static boolean isInstalledFamily(String name, java.awt.Font resolved) {
			if (installedFamilies().contains(name.toLowerCase(java.util.Locale.ROOT))) {
				return true;
			}
			return name.equalsIgnoreCase(resolved.getFamily())
					|| name.equalsIgnoreCase(resolved.getFamily(java.util.Locale.ENGLISH))
					|| name.equalsIgnoreCase(resolved.getFontName())
					|| name.equalsIgnoreCase(resolved.getFontName(java.util.Locale.ENGLISH));
		}

		/**
		 * The installed family names, lower-cased, read once per resource reload.
		 *
		 * <p>Both the default-locale and the English tables are merged, so a name is recognised in
		 * whichever spelling the pack used. An environment that cannot enumerate its fonts answers
		 * an empty set rather than throwing: {@link #isInstalledFamily} then decides on the
		 * resolved font's own names, which still recognises an installed family and still refuses
		 * a {@code Dialog} substitution.</p>
		 */
		private static Set<String> installedFamilies() {
			Set<String> families = systemFamilies;
			if (families == null) {
				synchronized (Resources.class) {
					families = systemFamilies;
					if (families == null) {
						families = new HashSet<>();
						try {
							final java.awt.GraphicsEnvironment environment =
									java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
							collectFamilies(environment.getAvailableFontFamilyNames(), families);
							collectFamilies(environment.getAvailableFontFamilyNames(java.util.Locale.ENGLISH),
									families);
						} catch (RuntimeException | LinkageError cannotEnumerate) {
							/* Leave the set empty; the resolved-name rule is the fallback rule. */
						}
						systemFamilies = families;
					}
				}
			}
			return families;
		}

		private static void collectFamilies(String[] names, Set<String> into) {
			for (String family : names) {
				if (family != null && !family.isEmpty()) {
					into.add(family.toLowerCase(java.util.Locale.ROOT));
				}
			}
		}

		/**
		 * What an uninstalled request is answered with, and why — the decision and the log line's
		 * reason in one value, so the two cannot drift apart.
		 */
		static final class Substitute {
			/**
			 * The font to draw with. Never {@code null}: a bundled file that cannot be read leaves
			 * the JVM's own substitution in place, which is what JCM 2.x would have drawn with.
			 */
			final java.awt.Font font;
			/** Why this font, phrased to finish the log line's "…, because …". */
			final String reason;

			private Substitute(java.awt.Font font, String reason) {
				this.font = font;
				this.reason = reason;
			}
		}

		/**
		 * The rule for a name this machine does not have, as one function of the substitute and the
		 * request name.
		 *
		 * <p>Package-private and static on purpose: it is what lets the check hand in a font whose
		 * glyph coverage it knows, so the branch a given machine does not take is still asserted on
		 * that machine. In game the substitute is always
		 * {@code new java.awt.Font(name, PLAIN, 1)}, built by the caller.</p>
		 *
		 * <h2>The three answers</h2>
		 * <ol>
		 *   <li>a name that marks a CJK script ({@link #markedScripts}) and a substitute that
		 *       draws every script it marks → the substitute, unchanged. This is the adaptive half:
		 *       a machine with a real Chinese font has a better answer than MTR's bundled serif CJK
		 *       face, and a name like {@code "Noto Sans CJK TC Medium"} is asking for a sans;</li>
		 *   <li>a name that marks a CJK script the substitute cannot draw → MTR's bundled CJK face,
		 *       the only bundled file with CJK glyphs at all;</li>
		 *   <li>anything else → the bundled face the name's style asks for: {@code "serif"} selects
		 *       the bundled CJK file, every other name the bundled Latin face.</li>
		 * </ol>
		 *
		 * <p>A bundled file that cannot be read (no client, no resource manager) leaves the
		 * substitute in place rather than throwing or answering {@code null}.</p>
		 */
		static Substitute substituteFor(java.awt.Font substitute, String name) {
			if (asksForCjkFace(name)) {
				/* A sans CJK family this machine really has beats both alternatives. AWT's own
				   substitution is not trustworthy here: it draws Han, but on this machine it draws it
				   with SimSun -- proven by identical pixel patterns for 下 and 班 -- and a Song/Ming
				   face beside the panel's Noto Sans Latin is the mismatch a reader notices first. MTR's
				   bundled CJK file is a serif too, so it is the last resort rather than the answer. */
				final java.awt.Font sans = installedSansCjkFace(name);
				if (sans != null) {
					return new Substitute(sans, "this machine has \"" + sans.getFamily()
							+ "\", a sans CJK face, and the request asks for " + markedScriptList(name)
							+ " in a sans style; MTR bundles no CJK sans face to prefer over it");
				}
				final java.awt.Font cjk = bundledCjkFace();
				return cjk == null
						? new Substitute(substitute, unreadableReason())
						: new Substitute(cjk, "no sans CJK family is installed here, so MTR's bundled serif"
								+ " CJK face -- the only bundled file that draws " + markedScriptList(name)
								+ " -- is the last resort (Java2D paints a missing-glyph box for a physical"
								+ " font instead of substituting one per character)");
			}
			if (isSerifRequest(name)) {
				final java.awt.Font serif = bundledCjkFace();
				return serif == null
						? new Substitute(substitute, unreadableReason())
						: new Substitute(serif, "the name asks for a serif face");
			}
			final java.awt.Font sans = bundledLatinFace();
			return sans == null
					? new Substitute(substitute, unreadableReason())
					: new Substitute(sans, "the bundled Latin Noto is the face a non-CJK name wants");
		}

		private static String unreadableReason() {
			return "MTR's bundled Noto could not be read, so JCM 2.x's own substitution is what is left";
		}

		/**
		 * The bundled Latin face — {@code mtr:font/noto-sans-semibold.ttf} — read once and shared
		 * with the {@code "Noto Sans"} branch above.
		 *
		 * <p>Read through {@link ScriptFonts#read}, which applies the script path rules and reports a
		 * failure once; the field is the cache, so a name that lands here twice loads the file once.
		 * Called from inside the caller's lock in game, and from the check on one thread.</p>
		 */
		private static java.awt.Font bundledLatinFace() {
			if (notoSans == null) {
				notoSans = ScriptFonts.read(NOTO_SANS);
			}
			return notoSans;
		}

		/** The bundled CJK face — {@code mtr:font/noto-serif-cjk-tc-semibold.ttf} — read once. */
		private static java.awt.Font bundledCjkFace() {
			if (notoSerif == null) {
				notoSerif = ScriptFonts.read(NOTO_SERIF);
			}
			return notoSerif;
		}

		private static boolean isSerifRequest(String name) {
			return name.toLowerCase(java.util.Locale.ROOT).contains("serif");
		}

		/**
		 * The scripts a request name marks, and the character each is probed with.
		 *
		 * <p>Every CJK face carries Han whatever else it carries, so a Japanese or Korean mark is
		 * <em>added</em> to Han rather than replacing it: {@code "Noto Sans CJK JP"} has to draw both
		 * 下 and あ, which is why a substitute with Han but no kana is not good enough for it.</p>
		 */
		private enum Script {
			/** A CJK ideograph: 下, "down" — a character no CJK face is without. */
			HAN('\u4e0b'),
			/** Hiragana あ. */
			KANA('\u3042'),
			/** The Hangul syllable 한. */
			HANGUL('\uD55C');

			private final char probe;

			Script(char probe) {
				this.probe = probe;
			}
		}

		/**
		 * Which scripts a request name marks: the ones it names as words, and the ones it is
		 * <em>written</em> in.
		 *
		 * <p>Either the name is written in CJK/kana/hangul ({@code "微软雅黑"}, {@code "メイリオ"}) or
		 * one of its words says so ({@code "Noto Sans CJK TC Medium"}, {@code "Noto Sans SC"},
		 * {@code "Source Han Sans"}). Only reached for a name that is not installed, so a false
		 * positive costs nothing but a wider face — which is why the style words that merely suggest
		 * a CJK face ({@code "gothic"}) are left in.</p>
		 */
		private static Set<Script> markedScripts(String name) {
			final java.util.EnumSet<Script> scripts = java.util.EnumSet.noneOf(Script.class);
			for (int i = 0; i < name.length(); i++) {
				final char c = name.charAt(i);
				if (c >= 0x3040 && c <= 0x30FF) {
					/* Kana, tested before the wider CJK range below, which contains it. */
					scripts.add(Script.KANA);
				} else if (c >= 0xAC00 && c <= 0xD7AF) {
					scripts.add(Script.HANGUL);
				} else if (c >= 0x2E80 && c <= 0x9FFF || c >= 0xF900 && c <= 0xFAFF) {
					/* Radicals, ideographs, and the compatibility ideographs. */
					scripts.add(Script.HAN);
				}
			}
			for (String word : name.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
				if (CJK_HAN_WORDS.contains(word)) {
					scripts.add(Script.HAN);
				}
				if (CJK_KANA_WORDS.contains(word)) {
					scripts.add(Script.KANA);
				}
				if (CJK_HANGUL_WORDS.contains(word)) {
					scripts.add(Script.HANGUL);
				}
			}
			if (!scripts.isEmpty()) {
				/* Anything that marks a CJK face at all is expected to have Han: this is the "only
				   cjk was mentioned" case, and it is what {@link #asksForCjkFace} reduces to. */
				scripts.add(Script.HAN);
			}
			return scripts;
		}

		/**
		 * A sans CJK family this machine actually has, or {@code null}.
		 *
		 * <p>Deliberately a short list of families that are sans by construction rather than a
		 * heuristic over every installed face: style cannot be read off a family name, and getting it
		 * wrong is visible on the panel. The weight suffix is tried first when the request names one,
		 * so {@code "Noto Sans CJK TC Medium"} lands on a Medium face where the machine has it --
		 * which is the weight the pack asked for in the first place.</p>
		 */
		private static java.awt.Font installedSansCjkFace(String name) {
			final Set<String> installed = installedFamilies();
			if (installed.isEmpty()) {
				return null;
			}
			final String asked = name.toLowerCase(java.util.Locale.ROOT);
			final java.util.List<String> suffixes = new java.util.ArrayList<>();
			if (asked.contains("medium")) {
				suffixes.add(" Medium");
			}
			if (asked.contains("semibold") || asked.contains("semi bold") || asked.contains("demi")) {
				suffixes.add(" SemiBold");
			}
			if (asked.contains("bold")) {
				suffixes.add(" Bold");
			}
			if (!suffixes.contains(" Medium")) {
				/* Every Noto CJK family ships a Medium, and a Medium face is what a split-flap panel
				   wants next: regular is thin beside the panel's semibold Latin. */
				suffixes.add(" Medium");
			}
			suffixes.add("");
			for (String family : SANS_CJK_FAMILIES) {
				for (String suffix : suffixes) {
					final String candidate = family + suffix;
					if (installed.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
						return new java.awt.Font(candidate, java.awt.Font.PLAIN, 1);
					}
				}
			}
			return null;
		}

		/**
		 * Sans CJK families, most wanted first.
		 *
		 * <p>Noto first because that is what packs ask for by name, then the other widely installed
		 * sans faces. Simplified and Traditional are listed separately: they are different families
		 * with different glyph forms and a machine can have either.</p>
		 */
		private static final String[] SANS_CJK_FAMILIES = {
				"Noto Sans SC", "Noto Sans TC", "Noto Sans HK", "Noto Sans JP", "Noto Sans KR",
				"Source Han Sans SC", "Source Han Sans TC", "Source Han Sans",
				"Microsoft YaHei", "Microsoft JhengHei", "PingFang SC", "PingFang TC",
				"Hiragino Sans", "Meiryo", "Yu Gothic UI", "Yu Gothic", "Malgun Gothic",
		};

		/** Whether the request name marks a CJK script — see {@link #markedScripts}. */
		private static boolean asksForCjkFace(String name) {
			return !markedScripts(name).isEmpty();
		}

		/**
		 * Whether {@code font} draws every script {@code name} marks.
		 *
		 * <p>{@code canDisplay} is the right question for <em>this</em> font and the wrong one for
		 * the bundled files: it answers whether the face has a glyph, and a physical font does not
		 * borrow one from another face for a character it lacks — see the method comment on
		 * {@link #getSystemFont(String)}. It is asked about the JVM's substitution, which is a
		 * logical, composite font and does substitute per character.</p>
		 */
		private static boolean drawsMarkedScripts(java.awt.Font font, String name) {
			if (font == null) {
				return false;
			}
			for (Script script : markedScripts(name)) {
				if (!font.canDisplay(script.probe)) {
					return false;
				}
			}
			return true;
		}

		/** The probe characters a name marks, for the log line: {@code "下"}, {@code "下+あ"}. */
		private static String markedScriptList(String name) {
			final StringBuilder list = new StringBuilder();
			for (Script script : markedScripts(name)) {
				if (list.length() > 0) {
					list.append('+');
				}
				list.append(script.probe);
			}
			return list.toString();
		}

		/**
		 * The words a font name uses to say which CJK face it is.
		 *
		 * <p>Split by script rather than kept as one list, because the probe is per script: a
		 * Japanese name has to draw kana as well as Han, and a Korean one hangul. The union of the
		 * three sets is the recognition set a name is classified by — the same one this port has
		 * used since the fallback was added.</p>
		 */
		private static final Set<String> CJK_HAN_WORDS = Set.of(
				"cjk", "han", "hans", "hant", "chinese", "chs", "cht", "sc", "tc",
				"songti", "heiti", "kai", "ming", "yahei", "pingfang", "gothic");
		private static final Set<String> CJK_KANA_WORDS = Set.of(
				"japanese", "jp", "jpn", "mincho", "meiryo", "hiragino");
		private static final Set<String> CJK_HANGUL_WORDS = Set.of(
				"korean", "kr", "kor", "malgun");

		/** {@code mtr:font/noto-sans-semibold.ttf} — see {@link #getSystemFont(String)}. */
		private static final ResourceLocation NOTO_SANS = new ResourceLocation("mtr", "font/noto-sans-semibold.ttf");
		/** {@code mtr:font/noto-serif-cjk-tc-semibold.ttf} — see {@link #getSystemFont(String)}. */
		private static final ResourceLocation NOTO_SERIF =
				new ResourceLocation("mtr", "font/noto-serif-cjk-tc-semibold.ttf");

		/** The cached fonts; {@code null} until asked for, and left {@code null} when unreadable. */
		private static volatile java.awt.Font notoSans;
		private static volatile java.awt.Font notoSerif;

		/**
		 * Branch 3's answers, keyed by the name the script asked for, so a renderer's per-frame call
		 * is one map lookup and the INFO line below is written once per name.
		 */
		private static final Map<String, java.awt.Font> resolvedByRequest = new java.util.concurrent.ConcurrentHashMap<>();

		/** The installed family names, lower-cased; {@code null} until first needed. */
		private static volatile Set<String> systemFamilies;

		/** Drops the font caches; called when the resource manager reloads. */
		static void resetFonts() {
			notoSans = null;
			notoSerif = null;
			resolvedByRequest.clear();
			/* The installed families do not change with a resource pack, but this is the one place
			   the class is told the world moved; re-reading them is cheap and keeps the reset whole. */
			systemFamilies = null;
			ScriptFonts.reset();
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
		/** Set once create() has run; see {@link #start(PIDSWrapper)}. */
		private volatile boolean started;
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
			/* create() is NOT run here. It needs the panel's pids object, and a compiled
			   program is not yet bound to a panel: this constructor is reached from
			   programFor(preset, panelKey), which is handed nothing but the key block's
			   position. See start(). */
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
		 * Runs the script's {@code create(ctx, state, pids)} once, with the panel's own pids.
		 *
		 * <h2>Why create() is not called when the program is compiled</h2>
		 * <p>It used to be, from the constructor, as {@code invoke("create", null, null)} -- so the
		 * pids argument was a Java {@code null}. A script's {@code create} that reads the object
		 * therefore died on its first statement with {@code TypeError: Cannot call method "..." of
		 * null}. {@code met transit}'s {@code met_running_board.js#10} does exactly that
		 * ({@code let pos = pids.blockPos();}, to name its saved-log file after the block), so on a
		 * real client {@code state.stationId} was never assigned and the panel went on to write
		 * {@code met_running_board/undefined_departed.json}.</p>
		 *
		 * <p>JCM 2.x never passes null there. Its {@code ParsedScript.lambda$invokeFunction$0}
		 * builds one three-element argument array
		 * ({@code getScriptContext(), ScriptInstance.state, ScriptInstance.getWrapperObject()})
		 * and calls <em>every</em> lifecycle function with it; {@code PIDSScriptInstance.<init>}
		 * sets the wrapper with {@code setWrapperObject(wrapper)} before any of them can run. The
		 * third argument is the real {@code PIDSWrapper} for create exactly as for render, which is
		 * the contract this port has to keep.</p>
		 *
		 * <p>The wrapper cannot be built at compile time: it carries the arrival list, the custom
		 * messages, the hidden rows and the platform filter, all of which are read from the world
		 * and the client's schedule cache at frame time. So create waits for the first frame that
		 * has one, which is also the first moment a script could observe anything create sets up.
		 * A script's create therefore still runs before its first render, and still once.</p>
		 *
		 * <p>Idempotent, and safe to call from either render path: whichever reaches it first wins
		 * and the other is a no-op.</p>
		 *
		 * @param pids the wrapper the render path built for this panel; never null, and a null is
		 *             ignored rather than run -- running create with a null pids is the bug this
		 *             method exists to end, so it must not be reachable from here
		 */
		public void start(PIDSWrapper pids) {
			if (pids == null) {
				return;
			}
			synchronized (this) {
				if (started) {
					return;
				}
				started = true;
			}
			/* The same three arguments render gets, so create may use ctx and pids as well as
			   state -- which is what the port's own scripts already assume. lenientArrivals is
			   deliberately not consulted here: create reads the panel, not the train list. */
			invoke("create", null, pids);
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
