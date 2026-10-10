# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.5

## Compatible MTR Version
MTR

## A font name the machine does not have is drawn in a face that can draw it, not in AWT's silent substitution

HKR Split-Flap Display asks `Resources.getSystemFont(...)` for three names. Only two of them —
`"Noto Sans"` and `"Noto Serif"` — are the names JCM 2.x reads out of MTR's own font files, so
`"Noto Sans SemiBold"` and `"Noto Sans CJK TC Medium"` both fall into its third branch,
`new Font(name, Font.PLAIN, 1)`: a lookup in the JVM's own font registry. A name that registry does
not have is **silently replaced**, and the panel is drawn in whatever face the JVM happened to pick
instead of the one the pack's author wrote down.

### What the substitution measured, on this machine

| Request | `getFamily()` | `getFontName()` |
|---|---|---|
| `"Noto Sans SemiBold"` | `Dialog` | `Dialog.plain` |
| `"Noto Sans CJK TC Medium"` | `Dialog` | `Dialog.plain` |

Neither name is in the registry — the only installed families holding a Noto name are `Noto Sans SC`,
`Noto Sans SC Medium` and `Noto Serif SC` — and AWT reports no error at all. JCM 2.x behaves the same
way on a machine without those fonts, which is why this is a **deliberate deviation from v2** and is
recorded as such in [`MTR3-PIDS-PORT.md`](MTR3-PIDS-PORT.md) §4.19.

### What the port does instead

The JVM lookup stays the **first** choice: a name this machine has is returned exactly as before, so
no machine that already drew a pack correctly changes. A name the registry does not have is answered
from MTR's bundled Noto (`mtr:font/noto-sans-semibold.ttf`, and the CJK
`mtr:font/noto-serif-cjk-tc-semibold.ttf`) through the existing `ScriptFonts` reader, its path guard
and its failure cache — except when the face AWT substituted for that name **already draws what the
name asks for**, in which case the substitution is kept, which is what JCM 2.x draws with. That
exception is not a nicety; the rule without it was wrong, and the subsection below says how.

"Does not have" is decided by two rules rather than one, because one is not enough in either
direction:

* a one-shot, lower-cased set of the installed family names, in the default locale **and** in
  `Locale.ENGLISH`. The English table is load-bearing on a Chinese-locale machine: here
  `getAvailableFontFamilyNames()` holds `Microsoft YaHei` while `new Font("Microsoft YaHei", …)
  .getFamily()` answers `微软雅黑`;
* the resolved font's own family and **face** names, ignoring case, in both locales. A face spelling
  is in no family table at all — `"Arial Bold"` resolves to family `Arial` and only `getFontName()`
  shows the machine has it — so the table alone would have sent a working font to the fallback.
  `Font.getName()` is deliberately not consulted: it returns the string the caller passed in, so it
  agrees with the request even for a name nothing has.

### Which bundled face, and why a CJK name is measured rather than assumed

The choice is `Resources.substituteFor(substitute, name)` — one function of the JVM's substitution and
the request name, package-private so the check can hand it a font whose coverage it knows and assert
the branch this machine does **not** take. Three answers, in this order:

1. the name **marks a CJK script** (`CJK`, `SC`, `TC`, `JP`, `KR`, `Hans`, `Hant`, … as a word, or a
   name written in CJK/kana/hangul) **and** the substitution draws every script the name marks → the
   substitution is **kept**. A Japanese mark is probed with `あ` and a Korean one with `한`, on top of
   the Han probe `下` that every CJK name has to pass. HKR's `"Noto Sans CJK TC Medium"` lands here on
   this machine: AWT substitutes `Dialog`, a *logical, composite* font that paints `下` (198 px) and
   `班` (443 px) as two real characters. Keeping it changes nothing about a panel that was already
   right;
2. the name marks a CJK script the substitution **cannot** draw → MTR's bundled CJK face
   (`mtr:font/noto-serif-cjk-tc-semibold.ttf`), the only bundled file with CJK glyphs at all;
3. anything else → the bundled face the name's style asks for: `serif` selects the bundled CJK file,
   every other name the bundled Latin `mtr:font/noto-sans-semibold.ttf`, which is the face HKR's
   `"Noto Sans SemiBold"` asks for.

**The first cut of this rule was wrong, and how it was wrong is worth keeping.** It said "a CJK name
takes the bundled CJK file" unconditionally, and the assumption underneath was that the Chinese
characters a bundled *Latin* file lacks would be filled in per character — which is how a browser, or
a composite font, behaves. Java2D does **not** do that for a physical font: a face that arrives
through `Font.createFont` has one face and no fallback chain, so `noto-sans-semibold.ttf` paints `下`
and `班` as the **identical 246-pixel missing-glyph box** (`.notdef`, the tofu square), while `Dialog`
— a logical, composite font — substitutes a face per character and paints the real glyphs. Both
consequences are in the rule above:

* a bundled file must never be chosen for a script it has no glyph for — hence the CJK file whenever
  the substitution cannot draw the script, even though the name asked for a sans;
* and a substitution that **already draws the script must not be thrown away** — hence the probe. The
  unconditional rule replaced a working `Dialog` with a serif face the pack never asked for.

`ScriptFontFallbackCheck` pins both halves, pixels included.

### Checks

| Check | What it pins |
|---|---|
| `ScriptFontFallbackCheck` (**new**) | `"Noto Sans SemiBold"` is the bundled Latin face rather than `Dialog`, and scales with the pack's own `deriveFont(BOLD, 40)`; `"Noto Sans CJK TC Medium"` is answered with a face whose `下` and `班` are two different drawings (not the same tofu box), whichever of the two paths this machine takes — and the check calls the decision function **directly with a Latin-only font**, so the branch this machine does *not* take is asserted anyway: it selects MTR's bundled CJK face, that face draws `下`, and the reason names the character the substitute lacks. The bundled Latin file is pinned as the tofu control (identical 246 px for both characters) and the bundled CJK file as the real-glyph side (293 vs 611 px). `"Noto Sans"` / `"Noto Serif"` are unchanged, still `Font.createFont` at size 1 out of their own files; `"Noto Sans SC"`, the face-spelling `"Noto Sans SC Bold"`, the logical `Dialog` and `Monospaced` all still come back as the machine's own fonts; the per-name cache is dropped by `resetFonts()`; **one** INFO line per name says which path was taken, what AWT substituted and why (captured through log4j, which the check raises to INFO for the duration); and with no resource manager the whole thing still soft-fails to the JVM's substituted font instead of throwing — including a CJK name, which needs no file at all |

The eleven-pack regression is unchanged at **108/112** — the packs' own scripts reach the bundled
fonts only in game (the headless pack run has no `mtr` assets), which is the soft-failure path this
change leaves exactly as it was.

## The Fabric manifest declares the dependency the classes always had, and HKR's terminus rule reads the panel's own platform

Nothing in this section adds anything to look at: it draws what 2.4 drew. The first item is a
manifest catching up with the classes the jar already ships; the second is a station name catching
up with the platform the panel is standing on.

### `fabric.mod.json` declares `architectury`

Count the `dev/architectury/**` entries in the merged jar: zero. The API is not bundled. But
`fabric/com/jsblock/JobanClient.class` links `dev.architectury.event.Event`, `ClientGuiEvent` and
`ClientTickEvent` directly -- it registers a HUD renderer and a client tick on them -- and the class is
reached from the declared client entry point (`JobanFabricClient` calls `JobanClient.init()`), so on
Fabric without Architectury API it cannot be linked at all: a `NoClassDefFoundError` raised from inside
a mod the loader had already accepted. Forge never had this shape, because `META-INF/mods.toml` has
marked `architectury` mandatory from the start. The Fabric manifest simply never mentioned it, and a
dependency a manifest does not declare is not one the loader checks.

`depends` now carries `"architectury": ">=9"`, and the range comes from the Fabric artifact's own
version scheme rather than Forge's. `dev.architectury:architectury-fabric:9.2.14` -- the version
`gradle.properties` pins as `architectury_version` -- opens with `"id": "architectury"` and
`"version": "9.2.14"` in its own `fabric.mod.json`, so Fabric-side Architectury versions are plain
semver and the 1.20.x line is 9.x. The `[1.26.37,)` in `mods.toml` is FML's version for the same
dependency and means nothing to the Fabric loader; copied across, it would demand a version that does
not exist there and refuse every Fabric install.

What changes for a user is the failure and nothing else: a Fabric instance without Architectury API is
now stopped by the loader, which names the dependency that is missing, instead of by a class that fails
to link later.

### HKR's "not in service" rule is answered from the panel's own platform

A panel wearing a filtered or an auto-detected platform filter could be told the wrong station by
`pids.station()`, and HKR decides "不載客列車 / Not in Service" from exactly that name: it walks the
service's calling pattern for the panel's own index and reports not-in-service when that index is the
last stop. Two ways it went wrong, and both are fixed by the engine resolving the panel's platform
the way MTR does — nearest the block — and handing that answer to `pids.station()`:

* a panel with no filter of its own had nothing to resolve a station from at all, so
  `pids.station()` answered `null`. HKR's rule cannot find an index without a name and answers "in
  service", so **a terminus panel drew a real departure instead of the badge**;
* a panel filtered to several platforms had its station taken from the filter's first element —
  and that set is a `Set<Long>`, whose order is hash order, not the panel's — so the name could be a
  station at the other end of the line. A mid-line panel wearing the terminus's name is "at" the
  last stop, which printed **"不載客列車" on a panel that has real trains**.

`PIDSWrapper` carries the resolved platform now, and a copy made for the lenient-arrivals retry
carries it too. `Text.marquee(number)` was checked at the same time and needed nothing: all 55
argumented call sites in the corpus pass a number literal, and Rhino resolves those against the
existing `double` overload. `ScriptHkrCheck` pins both, and the eleven-pack run is unchanged at
108/112.

### Checks

| Check | What it pins |
|---|---|
| `ScriptHkrCheck` | HKR's terminus rule on a real MTR world: three stations (`你好 → 测试 → 114514`), real `Station`/`Platform`/`Route` objects in MTR's own client caches, and the same preset run at the terminus and mid-line. The terminus has to answer `true` and the mid-line `false`, including for an auto-detected panel (no filter) and for a filter naming a platform at the other end of the line; `-HkrPack` also runs HKR's two shipped presets and asserts the badge and the route-map strip on their own draw calls. Its second half pins `Text.marquee(number)` |

## The importer globals every JCM 2.x script starts with, and the canvas a real pack paints its whole screen with

A board in the library came up **transparent**, with nothing on it at all. The cause was one line of
Rhino setup, and this section is what it took to find and fix it.

### The script never loaded, so there was nothing to draw

`HKR-Split-Flap-Display` ships one preset (`next_train`, "HKR Split-Flap Display") and one 81 KB
script, and apart from its thumbnail it has no textures: the entire board — title, service badges,
destinations, the stopping-at diagram, the flip animation — is painted into a `GraphicsTexture` with
AWT. On screen it was an empty panel.

`next_train.js#27` is:

```js
importPackage(java.awt);
importPackage(java.awt.geom);
```

The engine built its script scope with `Context.initStandardObjects()`, which answers a scope of
**standard objects** — the ECMAScript built-ins and nothing else. It has no `importPackage`, no
`importClass`, no `Packages`, and none of the `java` / `javax` / `org` / `com` / `edu` / `net`
package roots. So the preset died of

```
ReferenceError: "importPackage" is not defined. (next_train.js#27)
```

**while it was being loaded.** `create()` was never defined, so nothing was ever painted, and the
panel stayed transparent. Nothing in the log said "the panel is blank" in those words; it said the
script had thrown once, and a script that throws at load time looks exactly like a preset that draws
nothing.

JCM 2.x does not use `initStandardObjects()` either. `javap -p -c` of
`com.lx862.mtrscripting.core.ParsedScript.<init>` is `Context.enter` →
`setLanguageVersion(200)` → `setClassShutter(ScriptManager.getClassShutter())` →
`new ImporterTopLevel` / `ImporterTopLevel.<init>(Context)` → `putfield scope`. `newScope()` now
builds that same scope:

```java
ScriptClassShutter.install(cx, SHUTTER);
final Scriptable scope = new ImporterTopLevel(cx);
```

`ImporterTopLevel`'s own `initStandardObjects` is the very call `Context.initStandardObjects()` makes
internally (`Context.initStandardObjects(ScriptableObject, Z)`), followed by
`exportAsJSClass(3, this, false)` and `delete("constructor")` — so the built-ins are the same ones
and the importer is the only thing added. `newScope()` is the single entry point: the engine's
compile path and all eight headless checks go through it, so both sides are the same scope by
construction.

### The importer does not open a hole in the sandbox

The importer is a second route to a Java class, and a route that skipped `ClassShutter` would hand
every pack the whole JVM — so this was checked before anything else, against the reference jar and
then by running it. It does not skip the shutter:

* `importPackage` is **lazy**. `javap -p -c` of `ImporterTopLevel.importPackage(ScriptableObject,
  NativeJavaPackage)` shows it doing nothing but recording the package in an `ObjArray` associated
  with the scope — no classes are resolved, so no check is needed at that point.
* Resolution happens later, per name, in `ImporterTopLevel.get` → `NativeJavaPackage.getPkgProperty`,
  and that is where the shutter is consulted: the bytecode reads
  `Context.getClassShutter()` and calls `ClassShutter.visibleToScripts(fullName)` **four instructions
  before** `Kit.classOrNull`, and falls through to a `NativeJavaPackage` when the answer is `false`.

So a denied class resolves to a package object, not a class. Measured on the real scope:

| A pack writes | Result |
|---|---|
| `importPackage(java.lang); System.nanoTime()` | `ReferenceError: "System" is not defined` |
| `java.lang.System.nanoTime()` | `TypeError: Cannot call property nanoTime in object [JavaPackage java.lang.System]` |
| `Packages.java.lang.System.nanoTime()` | the same refusal |
| `importClass(java.lang.System)` | `EvaluatorException: Function importClass must be called with a class; had "[JavaPackage java.lang.System]"` |
| `importPackage(java.io); new File(".")` | `ReferenceError: "File" is not defined` |
| `importPackage(java.lang.reflect); Method.class.getName()` | `ReferenceError: "Method" is not defined` |
| `typeof System` after `importPackage(java.lang)` | `undefined` |

**No allow-list entry was added or widened for this.** `java.awt.*` and `java.awt.geom.*` were
already on `ScriptClassShutter`'s list — the runtime canvas put them there in 2.3 — and `importPackage`
resolves through the same rules as any other read. (For the record, JCM 2.x's own
`MTRClassShutter` does **not** allow `java.awt.*`: its allow-list is `net.minecraft.*`, `java.lang.*`,
`java.util.*`, `sun.java2d.*`, three `java.io` interfaces and `jdk.proxy1.*`, with `JCMScripting`
adding `org.mtr.*`, `mtr.*` and two of its own packages. A pack that needs AWT therefore needs the
player to have switched script restrictions off. This branch had already decided the other way for
its own canvas, and `ScriptShutterCheck` still pins the refusals on both routes.)

`ScriptImporterCheck` asserts all of the above on every build, plus the positive control that the
allowed half of the same importer still works.

### `Resources.getSystemFont(name)`

The script's second line of business after the imports is its typefaces, and it asks for them the way
the v2 docs describe: `base.deriveFont(style, size * k)`, never `new Font(family, ...)`, because a
font that arrives through `Font.createFont` is not registered with the `GraphicsEnvironment` and has
no family anything else can find.

`javap -p -c com.lx862.mtrscripting.util.ScriptResourceUtil` gives the whole method — three branches:

| Name | Reference implementation |
|---|---|
| `"Noto Sans"` | cached `Font.createFont` of `mtr:font/noto-sans-cjk-tc-medium.otf` when the class's `hasNotoSansCjk` flag is set, otherwise `mtr:font/noto-sans-semibold.ttf`; a failure logs and answers `null` |
| `"Noto Serif"` | cached `Font.createFont` of `mtr:font/noto-serif-cjk-tc-semibold.ttf`; a failure logs and answers `null` |
| anything else | `new Font(name, Font.PLAIN, 1)` — a JVM font-registry lookup, size 1, for the caller to scale |

The `hasNotoSansCjk` branch is not reproduced, and that is a reading of the reference rather than an
omission: `javap -p -c` shows the field assigned in exactly one place in that class, its static
initialiser, with `iconst_0` — it is `false` for the life of the process, so the `.otf` branch is
unreachable in the jar this port is measured against. MTR 3.6.3 does not ship that file either
(listing `libs/mtr-common.jar`: `assets/mtr/font/` holds `mtr.json`,
`noto-sans-semibold.ttf` and `noto-serif-cjk-tc-semibold.ttf`, and nothing else). The observable
behaviour is identical; the branch is documented in `Resources#getSystemFont`.

The three names HKR actually writes — `"Noto Sans CJK TC Medium"`, `"Noto Sans SemiBold"`,
`"Monospaced"` — all fall through to `new Font(name, PLAIN, 1)`, in JCM 2.x as much as here, so the
board is drawn in the JVM's default face. That is kept exactly: quietly upgrading those names to
MTR's bundled Noto would draw a different typeface from the one the pack's author saw.

### The canvas really does reach the screen — as far as a headless JVM can prove

The board's whole image is a `GraphicsTexture`, and this is the pack that finally exercises the GL
half of it. The chain was re-read against `javap -p -c` of the reference implementation, line by
line:

| Step | Reference | This port |
|---|---|---|
| `BufferedImage` | `new BufferedImage(w, h, 2)` = `TYPE_INT_ARGB` | identical |
| Rendering hints | `KEY_TEXT_ANTIALIASING = ON`, `KEY_STROKE_CONTROL = PURE` | identical |
| Upload loop | `for x in [0,w) for y in [0,h) pixels.setPixelRGBA(x, y, toAbgr(image.getRGB(x, y)))` | the same double loop, both bounds and both indices |
| `toAbgr` | `(a<<24) \| (b<<16) \| (g<<8) \| r` | identical |
| `NativeImage` | `new NativeImage(w, h, false)` | identical |
| `DynamicTexture` | `new DynamicTexture(image)`, which sets `setFilter(false, false)` — nearest neighbour, no mipmap | identical; built on the first `upload()` instead of in the constructor, so a canvas can be created, drawn on and closed with no game running |
| Registry | `Minecraft.execute(() -> textureManager.register(id, texture))` from the constructor | `textureManager.register(id, texture)` on the first upload |
| Actual GL | `RenderSystem.recordRenderCall(dynamicTexture::upload)` — deferred to the end of the frame | `texture.upload()` inline when called from the render thread, queued through `Minecraft.execute` otherwise |

Two deliberate differences, both consequences of deferring the GPU side: the texture is registered at
first upload rather than at construction, and the upload is not deferred through
`RenderSystem.recordRenderCall` when it is already on the render thread. Everything the script can
observe is unchanged.

`upload()` then announces itself once per canvas —
`PIDS script canvas 1088x608 uploaded as texture jsblock:dynamic/graphics/<uuid>` — which is the
handle an in-game report needs, and the only trace the GPU half leaves. **The GL half itself still
cannot be exercised off the game**, and that is stated where it matters rather than glossed: what the
headless checks pin is every step up to and including the byte-order swap
(`ScriptCanvasCheck.checkAbgrSwap`), the painted pixels read back out of the `BufferedImage`
(`ScriptCanvasCheck`, `ScriptImporterCheck`), and the degraded path when there is no GL context. See
`MTR3-PIDS-PORT.md` §4.17 for the in-game self-check.

The canvas is drawn as an ordinary texture: `GraphicsTexture` marks its identifier as externally
registered, so `ScriptTextures.resolve` never goes looking for a file behind it, and
`Texture.create(...).texture(canvas.identifier)` lands in the same translucent light layer as any
pack texture. **No nested offscreen** — the pixelation pass (`PIDSPixelatedPanel`) renders the
script's draw calls into its own target as it always did, and a canvas upload inside it changes the
bound texture, not the bound framebuffer.

### Two smaller things the same script found

* **`SoundManager`'s optional argument.** Rhino resolves a Java overload by *exact arity* — a
  five-parameter method called with four arguments answers
  `EvaluatorException: Can't find method ...playSound(string,null,number,number)`, it does not fill
  in a default. HKR plays its flip-board click as `sm.playSound(id, origin, volume, pitch)` with a
  three-argument `sm.playLocalSound(id, volume, pitch)` fallback, so both calls failed to resolve and
  the preset's own `catch (e) {}` swallowed the reason: silence, and nothing in the log. Both
  spellings are now overloads, defaulting the category through the same `toCategory` path that
  already answered MASTER for an unnamed category.
* **`ctx.setDebugInfo(...)` is not implemented, and is not invented here either.** A whole-jar scan
  of the reference implementation for the string `setDebugInfo` returns **zero hits** — it is not in
  the jar this port follows, so there is no name, signature or behaviour to port. The pack reaches it
  only when its own `DEBUG_INFO` flag is true (it ships `false`), and even then inside a
  `try { ... } catch (e) {}`, so it cannot affect the board. Recorded as a version difference between
  the pack's JCM and this reference build, not as an MTR 4 dependency.

### Checks

| Check | What it pins |
|---|---|
| `ScriptImporterCheck` (new) | The nine importer globals (`importPackage` / `importClass` / `Packages` / `java` / `javax` / `org` / `com` / `edu` / `net`) as the script sees them; the AWT and Java2D classes `next_train.js` draws with; `Resources.getSystemFont` in all three of its branches, including `Font.createFont` over `mtr:font/noto-sans-semibold.ttf` and `deriveFont(int, float)`; **twelve** refusals through the importer (`importClass`, `Packages`, the bare package roots, and `importPackage` of a denied package followed by a use), each one *using* the class rather than naming it, plus the positive control that the allowed half of the same importer still resolves; and the whole AWT canvas path as a script — `importPackage(java.awt)`, a derived font, `RenderingHints`, `new Color(int)`, `Graphics2D.fillRect` / `drawString` at 136×76×8 = 1088×608, pixels read back, one texture draw call, one degrade report for two uploads, and a release |
| `ScriptCanvasCheck` | Gained `checkAbgrSwap`: the ARGB→ABGR swap the upload performs, asserted directly — this is the last step of the pixel path that can be verified without a GPU, and getting it wrong swaps red and blue |
| `ScriptApiCheck` | Gained the optional-argument sound shapes HKR writes (both spellings, so an overload added for the short form cannot have broken the long one), and a preset whose `create()` catches its own failure into `state.err` and paints an error screen now **fails by name** instead of reporting green over a picture that says "PIDS script error" |
| `ScriptShutterCheck` | Unchanged, and still green — 24 rule assertions and four end-to-end refusals |

### The board, run for real

```
== script check: ...\_hkr_split\assets\jsblock\scripts\next_train.js (arrivals=2) ==
OK   compiled (67253 chars)
     state keys create() left behind: W,H,err,tex,fonts
OK   the preset's own create() recorded no error on state
OK   render() ran
     draw calls recorded: 1
       Texture(z=-0.0 pos=0.0,0.0 size=128.0x72.0 texture=jsblock:dynamic/graphics/e5ce9b9a-... uv=0.0,0.0,1.0,1.0 color=FFFFFFFF)
OK   render() produced draw calls
OK   all 1 texture/rectangle colours are opaque
OK   dispose()

RESULT: SCRIPT OK
```

Green at 0, 1, 2 and 4 arrivals — `state.err` is `null` at every count, so the board is drawing its
own content and not the script's error panel. `.\tools\run-pids-check.ps1` with that pack added
(`-Script ...\next_train.js -ResourceRoot ...\_hkr_split`) ends on `RESULT: ALL CHECKS PASSED`, and
without it on the same line. The nine real packs re-run after all of this: **112 scripts, 108 passed,
4 failed** — the same four as 2.4, none of them a missing API.

One caveat that belongs with that number, found while re-running it: `World_PIDS-Pack-200`'s
`london_bus.js` reads the wall clock. Its `animation_arrive()` bumps three row indices from 1/2/3 to
4/5/6, and then to 7/8/9, once per second-that-ends-in-9 — and never resets them except on a later
such second, so in a long session the indices are out of range **two thirds of the time**. The row-2
and row-3 branches (and row 1, which uses `varA`) then call `pids.arrivals().get(x)` and dereference
the result **without a null check**, so `null.routeNumber()` throws on line 130.

It is the pack's own latent bug, it predates this work, and it is not subtle once seen: copying the
script to a scratch directory and forcing `let sec = 9` reproduces it every time, on any clock. In
game the engine's lenient-arrivals retry turns the throw into placeholder rows rather than a black
panel, which is why nobody had seen it. A single `ScriptApiCheck` is a fresh process that renders
three frames in well under a second, so it only trips when a render crosses that one-second boundary:
measured at **2 failures in 20 runs**, which makes a whole regression suite land on 107 rather than 108
roughly one run in six. The four full runs done here came out **108, 107, 107, 107**, and re-running
that one pack alone came out **66/66** — so the library figure is **108 of 112**, with 107 on a run
that catches `london_bus.js` mid-tick. The failing set never changed: the same four scripts as 2.4,
plus that one flake.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.4

## Compatible MTR Version
MTR

## Completing the v2 script API surface, and unblocking two more of met transit's presets

Two things a JCM 2.x script can do were still impossible here, and both of them are load-bearing for
a pack that is actually in the library — not for a hypothetical one. Everything below was found by
running the packs' own scripts, not by reading the docs: 112 `.js` files from nine resource packs,
108 of them now pass (`V2-API-覆盖表.md` has the per-script table).

### `Files` — persistent state between frames

Ported from `com.lx862.mtrscripting.util.FilesUtil`, registered as the global `Files` exactly as
`ParsedScript` does it (`javap -p -c`: `ldc String Files` → `ldc class .../FilesUtil`).

A PIDS panel is redrawn from scratch every frame, so a preset's own variables survive nothing. The
one preset in the library that needs to remember something is met transit's running board, and it is
the whole reason this exists:

```js
if (Files.hasData("met_running_board", fileName)) {
    let savedData = JSON.parse(Files.readData("met_running_board", fileName));
}
Files.saveData(JSON.stringify(saveData), "met_running_board", fileName);
```

Without the global that preset's first line is a `ReferenceError` and the panel never draws. With
it, the check prints the three lines that prove it reached the disk:

```
[PIDS script] [MET Running Board] Station ID: 0_64_0
[PIDS script] [MET Running Board] Loading from: met_running_board/0_64_0_departed.json
[PIDS script] [MET Running Board] No saved data found
```

All five of v2's methods are here — `read`, `readData`, `saveData`, `deleteData`, `hasData` — with
v2's signatures and v2's two roots (`<game dir>` for `read`, `<game dir>/data/mtrscripting` for the
rest), so a pack that kept data in JCM 2.x finds its own files. Each path element is joined and then
checked against its root, which is v2's own `resolvePathSafe` / `ensurePathNotEscaped`, and the
refusal keeps v2's wording (`Path must be within the "…" directory!`). Seven shapes of escape are
refused in `FilesCheck`, across all four methods.

Two deliberate differences from v2, both explained in `FilesUtil`'s own comments: the roots are
resolved lazily rather than in a static initialiser (v2's cannot load without a client, and this
port has a headless check that has to put the global on a scope), and the test-only root override is
package-private — because Rhino exposes *every* `public static` member of a global's class to a
script, so a public setter there would let a preset choose where its own files are written.

### A script whose file name has capitals in it now loads

`met transit` ships `assets/jsblock/scripts/Digital_Rail.js` and `Cyberpunk_Transit.js` — those
names, byte for byte, in the archive — and lists both in its `scriptFiles`. Neither could be read,
for two independent reasons:

1. `ResourceLocation` refuses a capital outright. Measured, not assumed:
   `ResourceLocationException: Non [a-z0-9/._-] character in path of location: jsblock:scripts/Digital_Rail.js`.
2. The lower-case spelling does not help either, because Minecraft's pack loader lower-cases the
   **first letter of every path segment** while indexing a pack — `Digital_Rail.js` is indexed as
   `igital_Rail.js`, which is not its name.

So a third of that pack's presets never compiled. `ScriptPackFiles` closes it by asking the packs
themselves, after the resource manager has already failed: the pack list comes from
`Minecraft.getResourcePackRepository().openAllSelected()`, a directory pack is walked with a
case-insensitive comparison at each level, and a zip pack is opened and its entries matched. Nothing
on that path builds a `ResourceLocation`, so nothing on it is subject to the spelling rule.

Reads that do go through the resource manager are folded first, which is the other half of the same
tolerance and what makes `include()` of a capitalised name work.

**This is a kindness to packs, and not Minecraft's rule.** Every log line and every comment says so,
and `ScriptCaseCheck` pins the part that matters: `..`, an absolute path and a backslash are still
refused on both paths. A capital is not a way out of a pack.

### Checks

| Check | What it pins |
|---|---|
| `FilesCheck` | The storage global: save/read round-trip, `null` for a file that was never written, UTF-8 and multi-line values, `saveData` creating its directories, `deleteData` being idempotent, seven escape shapes × all four methods refused with v2's wording, the whole API driven from inside a real script (Rhino, real scope, real sandbox), and the test-only root override being unreachable from one |
| `ScriptCaseCheck` | Reference spelling: an exact reference is **not** folded, a capital folds into a usable location, six escape shapes stay refused on both paths, the candidate order, and the disk lookup against a temporary pack tree — including that the answer carries the on-disk spelling rather than the requested one, which is the thing a case-insensitive filesystem would otherwise hide |
| `ScriptApiCheck` | Unchanged assertions; it now accepts several resource roots so a real pack can be run, and its `include` gained the same case tolerance the engine has |

### Real packs, for the record

| Pack | Scripts | Passed |
|---|---|---|
| `met transit` | 14 | 14 |
| `GURIGRUI_PIDS_JCM2.2.1_MTR4.0.5` | 1 | 1 |
| `World_PIDS-Pack-200` | 66 | 66 |
| `US PIDS Pack v4.2` | 21 | 20 |
| `琼岭追加包26.8.3` | 4 | 2 |
| `HKR PIDS` | 2 | 2 |
| `上海地铁-PIDS` | 2 | 1 |
| `Japan_Style_PIDS日式PIDSv1.0.6` | 1 | 1 |
| `Japanese_PIDS v1.5` | 1 | 1 |
| **total** | **112** | **108** |

None of the four failures is a missing API. Two are `PIDSUtil` helper modules that are `include`d
rather than run as presets and have no `render()` by design; two are presets that dereference
something a headless JVM has not got (`pids.station()` with no world, `MinecraftClient.localPlayer()`
with no player) without a null check. All four are recorded with their line numbers and the reason
in `V2-API-覆盖表.md`, and the two environmental ones are marked **not applicable** rather than
worked around.

### What is still missing, and why it stays that way

`Resources.read` / `readString` / `readFont` / `idr` / `getNTEVersion*` came up in the scan, and
every one of those call sites is in `assets/mtr/**` — a different scripting host's files (MTR's map
and LCD scripts, Java2D, vehicle data, an NTE version check), not PIDS presets. Those seven files
belong to a script surface this branch does not implement, and adding the API would not make a single
PIDS preset run. `VanillaText`, the TSC data classes and `getRenderManager()` are in the same
position: zero call sites in any PIDS script.

**The whole table, including the bytecode evidence for each row and the list of what was inferred
rather than measured, is `V2-API-覆盖表.md` at the workspace root.**

# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.3

## Compatible MTR Version
MTR

## This one adds JCM 2.x abilities rather than fixing bugs

Three things a v2 script can reach for were still missing here, and one thing it could reach that it
never should have. All four came out of the NeoJCM bytecode analysis (`NeoJCM-分析报告.md`), which is
why each one names the JCM 2.x class and method it was ported from — the point is parity with what a
v2 resource pack already does, not a new API of this fork's own.

### `ctx.parseComponent(jsonString)` — the declarative components, from a script

JCM 2.x's only entry into its component system is `PIDSScriptContext.parseComponent(String)`, which is
`JsonParser.parseString` into `PIDSComponent.parse(JsonObject)` and nothing else. This branch has had
the whole component system — all eleven registered types, the JSON `components` array, the layout
engine — but no way for a script to touch it.

```js
const clock = ctx.parseComponent('{"component":"clock","x":4,"y":2,"width":40,"height":10}');
if (clock.canRender()) {
    clock.render(ctx);
}
```

The parameter list is the one thing that differs, and it has to: v2's component renders with
`render(PoseStack, MultiBufferSource, Direction, PIDSContext)`, and a script has none of those — they
belong to the frame the engine is drawing. v2's own `ctx.draw(component)` cannot help either, because
it accepts only `PIDSDrawCall` and throws `"1st parameter is not a DrawCall!"` for a component. So the
component is handed the frame's state by the engine and a script calls `render(ctx)`; `canRender()`,
and the four geometry getters, are v2's public surface otherwise unchanged. `ctx.draw(component)` works
too, since that is what an author who has just written `parseComponent` reaches for next.

A declaration that cannot be used is refused by name — malformed JSON, an array, a missing `component`
key, an unknown type (the message lists the eleven known ones). v2's `PIDSComponent.parse` answers
`null` for an unknown type, which is right for a JSON preset that must keep loading its other
components and wrong for a script, which would fail later with "cannot call method render of null".

### A canvas a script draws into: `new GraphicsTexture(width, height)`

Ported from `com.lx862.mtrscripting.util.GraphicsTexture`: the same class name, constructor, public
fields (`identifier`, `bufferedImage`, `graphics`, `width`, `height`), `upload()` and `close()`.
What it adds is the part a PIDS board needs — `fillRect`, `drawText`, `measureText`, `drawTexture`
(to paste a pack texture in) and `clear` — and one change of ordering: v2 allocates its
`DynamicTexture` in the constructor, this port allocates the Java2D image immediately and the GL
texture on the first `upload()`. A canvas that is created and never uploaded therefore costs no
texture at all, and the whole create-draw-close path runs with no game running, which is how the
headless check covers it.

```js
const canvas = new GraphicsTexture(128, 32);
canvas.fillRect(0, 0, 128, 32, 0x101010);
canvas.drawText("06:00", 4, 4, 0xFC9700, 20);
canvas.drawTexture("jsblock:textures/block/pids/plat_circle.png", 100, 4, 24, 24);
canvas.upload();
Texture.create("board").texture(canvas.identifier).pos(0, 0).size(128, 32).draw(ctx);
```

Colours are ARGB with the surface's usual shorthand (`0xRRGGBB` means opaque, as `.color()` does;
`0` means transparent). They are taken as `long`, not `int`: a JavaScript number is a double, and
Rhino refuses to narrow `0xFF102030` into an `int` — every full-ARGB literal has the high bit set, so
an `int` parameter would make the notation unusable.

Textures are the one thing on this surface the garbage collector cannot reclaim — a `DynamicTexture`
holds a GL name and a native image — so `close()` is required, and there are two backstops for a pack
that forgets. A script may hold 64 canvases before the next is refused with a message naming
`close()` (JCM 2.x has neither limit nor cleanup), and a resource reload releases whatever was left
open, because nothing else can reach a script's canvases once its program is dropped.

`Resources.readBufferedImage(id)` came with it — it is v2's method, it was listed as missing, and it
is how a script gets a pack image into a canvas without a texture in between.

### `MTRClientData` is MTR's client data, not a second name for `MinecraftClient`

The analysis report suggested aliasing this mod's `MinecraftClient` global to `MTRClientData`, on the
assumption that v2's two client-data globals are one object under two names. The bytecode says
otherwise: `JCMScripting.lambda$register$0` reads `ldc class mtr/client/ClientData` for
`MTRClientData`, while `ParsedScript` reads `MinecraftClientUtil` for `MinecraftClient`. They are two
different classes in v2, and a port that conflated them would leave exactly the scripts the global
exists for — `MTRClientData.STATIONS`, `.PLATFORMS`, `.SCHEDULES_FOR_PLATFORM` — reading `undefined`
and drawing nothing.

So `MTRClientData` is `mtr.client.ClientData`, which MTR 3.6.3 has unchanged. The sandbox had to allow
it and the four types those maps hand out, because Rhino consults the class shutter when it wraps a
class at all — the first attempt at this installed the global and failed every script with
`Access to Java class "mtr.client.ClientData" is prohibited`, which the headless check caught before
anything shipped. JCM 2.x allows `mtr.*` wholesale; this allows the five classes a PIDS script can
actually reach through that global.

### A script can no longer read outside its own resource pack

`include()` and `Texture.texture(...)` both take a resource location, and a resource location accepts
`jsblock:../../../../../../etc/hosts` — dots and slashes are legal path characters. For a **folder**
resource pack, which is what a pack in `resourcepacks/` is and what every pack is in a development
environment, Minecraft resolves that at the filesystem level, where `..` means what it always means.
A script could read any file the game can and get the contents back in the log.

Both entry points now validate before anything is resolved or opened, and refuse `..` as a segment, a
leading `/`, a backslash, and a drive letter. The refusal is one console line naming the reference and
the reason, plus one chat line, and the script keeps running — `include()` returns without loading,
a texture resolves to Minecraft's missing-texture placeholder. This is
`FilesUtil.ensurePathNotEscaped`'s rule (JCM 2.x needs it for its `Files` global, which writes into
the game directory) applied to the two reads this branch has; the physical-path half is ported too and
is what the check exercises against real directories.

### Checks

Two new headless checks, plus additions to the existing ones, all of which run from
`tools/run-pids-check.ps1`:

| Check | What it pins |
|---|---|
| `ScriptPathCheck` | A decoy file **outside** the resource root, which a script's `include("jsblock:../../../secret.js")` must not read — asserted by the global it would have set, not by whether a guard threw. Plus the rule table, the physical guard on a real directory tree, and the same refusal through `Texture.texture()` |
| `ScriptCanvasCheck` | That drawing a canvas really paints pixels (read back and compared), that `upload()` with no client degrades instead of throwing, that `close()` releases it, that the 65th unreleased canvas is refused, and that a resource reload releases what a script left open |
| `ScriptApiCheck` | `ctx.parseComponent` from inside a real script (a clock: parse, rectangle, `canRender`, `render`, `ctx.draw(component)`, and all five refusals), and that `MTRClientData` resolves to `mtr.client.ClientData` and is readable through the shutter |

The canvas check is the one place a real engine cannot be run — there is no GL context in a headless
JVM — so it asserts the degraded path instead and says so in its own header.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.2

## Compatible MTR Version
MTR

## The PIDS Projector can be told what to display

A scripted preset on a projector read `pids.getCustomMessage(i)` and always got `""`: the projector's
screen had no fields for the panel's text, its C2S packet carried none, and the S2C packet that opens
the screen carried none either. Three places, all three written from scratch when this port added the
projector in 1.5.

**JCM 2.x does it by inheritance**, which is why it never had this problem:

| JCM 2.x | Carries |
|---|---|
| `PIDSProjectorScreen extends PIDSScreen` | the message fields, the row-hidden boxes and the hide-platform switch, for free |
| `PIDSProjectorUpdatePacket extends PIDSUpdatePacket` | `customMessages, rowHidden, filteredPlatforms, hidePlatformNumber, presetId` + the projector's seven doubles |
| `PIDSProjectorGUIPacket extends PIDSGUIPacket` | the same fields, so the screen opens showing what is already configured |

The port now carries the same fields, in JCM 2.x's own order, on both packets and in both directions:

```
pos, messages[], rowHidden[], platforms[], hidePlatformNumber, presetID, [7 doubles]
```

`hidePlatformNumber` needed a home on the projector's block entity, so it has one now (with its NBT key),
and the renderer reads it for projectors the way it already did for the RV boards.

A check was added to prove the two directions agree field for field before shipping, because a mismatch
there is a disconnect rather than a wrong label — and this projector has been there once already, with
its platform set in 1.5.

## And: the other two screens opened blank, and could wipe what they could not see

The same investigation turned up the same class of bug on the RV and 1A/LCD screens, in the other
direction. Their open-screen packets carried no `customMessages`, `rowHidden` or `filteredPlatforms`, so
the screens read those from the **client's own copy of the block entity** instead. Whenever that copy had
not caught up -- joining a world, a block changed a moment earlier -- the fields opened empty, and closing
the screen sent those empties back: a board that had been configured **lost its text**.

Both packets now carry the data, and the screens use the packet's copy with the entity as a fallback.
JCM 2.x never had this because `PIDSGUIPacket` carries exactly these fields.

All six packet directions (C2S and S2C, three families) are compared field by field by a script before
committing, because a mismatch there disconnects the client rather than showing something wrong.

## The Projector draws every kind of preset, and frames them all

Two more things the projector got wrong, and both for the same reason: only the scripted path knew
where a projector's panel actually is.

| A preset with... | is drawn by | and used to land |
|---|---|---|
| a script | `renderScripted` -- the one path that knew about projectors | the panel |
| a `components` array | `renderLayout`, whose matrix chain is JCM 2.x's **RV panel literals** | inside the block |
| only a texture (what a traditional, non-JS pack contains) | the built-in renderer, whose chain makes the same assumption, in two places | inside the block |

All three use the projector's own chain now — its offset, rotation and scale — while keeping their own
units-per-block, so RV, SIL, 1A and LCD render exactly as before.

The frame had the same shape of problem: it was drawn from `renderScripted` alone, so only JS presets
were framed. JCM 2.x draws it from its renderer, which is why every preset gets it there. It is drawn
from the layout and built-in paths too, and JCM 2.x's projection rectangle came with it: four red lines
around the area the panel will cover, 1.785 by 1 blocks per unit of scale, drawn only while the
projector is unrotated — a plain rectangle stops describing the panel as soon as it is turned.

## Also: how a pack shows express versus local

The pack-author guide gained a section on it, in both languages. The short version: **neither MTR 3 nor
MTR 4 has an express/local field**, so the packs that care put the service word in the route's **number**
(HKR's `getColorByKeyword`, and the same table again in Japanese_PIDS) and colour the badge by keyword.
On MTR 3 that number is gated behind the checkbox MTR labels *Has Route Number*, and it is the route's
property rather than the departure's — so express and local services need to be separate routes, or every
train on a mixed route shows the same label.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.1

## Compatible MTR Version
MTR

## Pixelation grows a second shape, and the pack gets a say

1.4 could pixelate a panel: draw it small, magnify it with nearest filtering, and every element lands
on one coarse grid. That grid was always squares, because squares are what a magnified image is, and
the only person who could ask for it was a player editing their own config -- a pack that wanted its
whole board drawn that way had to repeat itself on every preset, and a pack that did *not* want it had
no way to say so.

### Choose the shape

`pixelShape` picks what one pixel of that grid is:

| Shape | What it looks like | For |
|---|---|---|
| `square` | pixels edge to edge | a blocky LCD -- this is what 1.4 drew, and what happens when nobody says anything |
| `circle` | round dots with a dark gap between them | a dot-matrix board |

The round dots are one mask cell per offscreen pixel, tiled over the magnified image, so the cost is
one extra quad per panel rather than a shader or a second pass.

A player sets it per preset in the client config, exactly where the scale already lives:

```json
"pixelShapeByPreset": { "nanbin_crt_pids_1": "circle" },
"pixelShapeDefault": "circle"
```

`pixelShapeDefault` covers every preset they have not named. A pack declares it per preset the same
way it declares the scale (`"pixelShape": "circle"`).

### The pack gets a say in whether, and how coarse

A pack can now put a `pixelation` block at the top level of its `joban_custom_resources.json`:

```json
"pixelation": { "enabled": true, "scale": 3, "shape": "circle" }
```

- `enabled` is the pack saying whether it wants its presets pixelated at all -- the one thing a pack
  previously could not say, since a preset carrying a `pixelScale` was pixelated whether the pack
  liked it or not
- `scale` is the resolution it wants by default, so a pack no longer repeats `"pixelScale": 3` on
  every entry
- `shape` is the grid it drew for

Precedence, unchanged in spirit from 1.4: the player's entry for a preset, then the player's default,
then the preset's own declaration, then the pack's, then off/square. Nothing that worked before
changes meaning, and a pack that says nothing behaves exactly as it did.

### The pack can name the grid in dots

There is a guide for pack authors now, in both languages:
**[docs/pixelation-guide.md](docs/pixelation-guide.md)** / **[docs/pixelation-guide.zh.md](docs/pixelation-guide.zh.md)** -- every key, the exact
proportions of the three canvases, and a table of which dot counts look like what.

`pixelScale` is a divisor, so it only lands on grids that divide the canvas evenly -- 136/3 is not a
whole number of dots. A pack that knows the board it drew for can now just say so:

```json
"pixelResolution": 96                                  // 96 dots across; height from the canvas
"pixelResolution": [96, 54]                            // or both, explicitly
"pixelResolution": { "width": 96, "height": 54 }
```

It works per preset, and in the pack-level `pixelation` block for every preset the pack has, and the
more precise of the two ways wins over `pixelScale`.

**The proportions always come out the canvas's.** The magnified target is stretched across the whole
panel, so a grid whose ratio differs from the canvas's is a squashed picture -- and 96x54 on a 136x76
board is exactly that, by about one percent. The width is read as the intent and the height recomputed
from it and the canvas; when that changes the pack's number, one log line says so and why. A grid of
`96` on a 136x76 canvas becomes `96x54`, and on the 1A canvas `96x31`.

`tools/run-pids-check.ps1` grew a fourth check for this: `PixelationCheck`, which asserts the parse
forms, the scale fallback, the aspect correction, and the ratio invariant across three canvases and
six widths. It runs headlessly, which is the point -- the sizing is arithmetic, and the aspect rule was
the part most likely to be quietly wrong.

### Route type on an arrival

Not related to pixelation, but small enough to travel with it. MTR 3 has `Route.routeType`
(`NORMAL` / `LIGHT_RAIL` / `HIGH_SPEED`) and it reaches the client, so an arrival now exposes
`routeType` and `isLightRailRoute`.

This is a fork-only extension and the documentation says why: **neither MTR 3 nor MTR 4 has a notion
of express versus local**, and MTR 4 has no route type at all. A preset asking for "快慢车" is asking
for something the data does not contain; what it can do is colour by the route type, or match a
keyword in the route name, which is what the packs that care already do.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.0

## Compatible MTR Version
MTR

## Why the version jumps to 2.0

1.5 could draw every preset we had. What it could not do was run a preset written against the
scripting documentation rather than against the presets we happened to test. This release closes
that gap: the scripting surface is now the documented Common APIs
(<https://jcm.joban.org/v2.2/dev/scripting/>), and two real packs that failed
on it -- World PIDS-Pack (65 presets) and 琼岭追加包 -- now render.

It also adds a block, and the two together are more than a patch release's worth.

## New: Fire Alarm

Ported from JCM 2.x's `FireAlarmWallBlock`. A wall-mounted bell: press it and it rings and puts out
a redstone signal of 15 for one second, which is how a station's alarm circuit hears about it. The
state is named `unpowered`, exactly as JCM 2.x names it, so a pack written against their block still
lines up. It crafts from iron, redstone and paper, two at a time, as theirs does.

One deliberate difference: JCM 2.x's version is silent, and a bell that cannot be heard is not much
of a bell, so this one rings.

## New: the scripting API a pack actually expects

Everything below was found by running real presets, not by reading the docs and guessing. Each one
was, until this release, a script that stopped dead or a panel that stayed black.

| Added | What it fixes |
|---|---|
| `Matrices` | `new Matrices()` was a ReferenceError; a clock hand could not be rotated |
| `Vector3f` | `pids.blockPos()` had to return one, with `x()`/`y()`/`z()` as methods |
| `Timing` | `Timing.currentTimeMillis()` and friends |
| `StateTracker`, `CycleTracker` | the documented way to notice a change once rather than every frame |
| `BackgroundWorker` | its absence failed a whole script *at load*, which is a black panel |
| `Networking` | `fetch`, `fetchString`, `fetchImage` -- what that worker was fetching |
| `console` | packs log through it, and the calls sit in catch blocks, so its absence turned one failure into two |
| `SoundManager` (`ctx.getSoundManager()`) | announcements; without it the pack stops at the first render |
| `TickableSoundInstance` | a sound a script holds and adjusts while it plays |
| `MinecraftClient.localPlayer()` and `PlayerEntity` | deciding whether to draw at all, by player distance |
| `MinecraftClient.displayMessage/narrate/renderDistance/gamePaused/lightLevelAt/worldIsRainingAt` | the rest of that page, a few lines each |
| `Resources.getMTRVersion()`, `Resources.getAddonVersion()` | packs branch on which mod versions they are running under |
| `scriptTexts` | JavaScript written inline in the preset entry, run before `scriptFiles` |
| `SCRIPT_INPUT` | the preset's own JSON, handed to its scripts |
| `Station.getColor/getColorHex/getId/getHexId` | RUHR reads the station colour; without it, `ruhr` failed every frame |

## Fixed

Each of these was a bug on this side, not a missing feature, and two of them had been quietly wrong
for a while:

- **`pids.type` reported the preset id instead of the block type.** A script branching on
  `pids.type == "pids_projector"` never took that branch, and a preset's `blacklist` -- a list of
  *type* names -- could never match anything.
- **`pids.blockPos()` returned an `int[]`.** Packs write `pids.blockPos().x()`, which is a method
  call, so those scripts ended on the spot.
- **Arrays handed to scripts had no prototype.** `new NativeArray(...)` built from Java has neither
  prototype nor parent scope, so `.map()`, `.slice()` and `.findIndex()` -- which is exactly what
  packs call on the result -- are unreachable, and Rhino reports it as *"Cannot find default value
  for object"*, an error that names nothing useful. Arrays are now built through the script's scope.
- **Route stops had no `stationName`.** Thirty-three scripts in one pack read
  `getPlatforms().toArray().map(platform => platform.stationName)`.
- **`getPlatforms()` had no `toArray()`** at all.

## Known issues

- The PIDS Projector's number fields still need a click before they show their value. Unchanged from
  1.5; the values themselves are correct.
- `Networking` requests do not use a proxy unless the JVM is told about one. On a machine that
  reaches the internet through a local proxy, a pack's weather lookup will time out; the pack
  handles that itself and keeps drawing.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-1.5

## Compatible MTR Version
MTR

## Changes
- **The PIDS Projector, ported from JCM 2.x.** One invisible block that projects a preset's panel
  into the air at an offset, a rotation and a scale of your choosing, with a configuration screen of
  its own: the preset (with the same suggestion list as every other PIDS screen), position offset,
  rotation, scale, and MTR's platform filter. It draws ordinary presets, so every scripted one works
  on it, pixelation included, and the direct render path is untouched.
- **A guide frame.** With a brush in hand the panel's edges are outlined, so a projector can be
  aimed without guessing where its panel will land.

## Known issue
- The projector screen's number fields show their value only after being clicked. The values are
  correct — they are saved and applied either way — but the text is not drawn until the field has
  focus. The widget involved is MTR's own, and the two obvious causes (setting the value in the
  constructor, and setting it in `init()`) have both been tried. To be fixed separately.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-1.4

## Compatible MTR Version
YMTR

## Changes
- **Whole-screen pixelation** — the dot-matrix look packs drawn for a low-resolution screen are
  imitating. A preset's whole output is drawn into a small offscreen target and magnified with
  nearest-neighbour filtering, so its text, icons and colour blocks all land on one coarse grid
  instead of staying smooth. Off by default: a preset pixelates only if it is listed in the
  client config's `pixelScaleByPreset`, keyed by preset id, so nothing needs to change in any
  resource pack and every existing preset renders exactly as before. `.js`-side API unchanged.

  Getting there took four rounds, each a different bug: a mirrored offscreen pass culled every
  quad as a back face; the framebuffer was restored by guesswork and took the held item and the
  player model with it; the hand-written orthographic matrix was transposed because JOML's
  `set(...)` is column-major; and finally `RenderType.end` turned out to bind the main framebuffer
  itself on every single draw, through the output state Minecraft builds for every layer. The port
  document records all four, since three of them are invisible unless you already know where to
  look.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-1.3

## Compatible MTR Version
YMTR

## Changes
- **The slanted SIL signs work.** The two SIL shapes are V-shaped: their halves face opposite ways,
  so one 22.5-degree lean comes out mirrored and forms the V. YJCM's own renderer leans the panel
  with `mulPose(XP.rotationDegrees(rotation))` between the facing rotations and the panel
  translate; this port never did, so a script panel was drawn flat and straight through the middle
  of the V. The lean is supported now, and the two SIL shapes carry their own translate —
  (-0.21, -0.410, -0.520) against the plain RV's (-0.21, -0.14, -0.114) — because YJCM's renderer
  is handed (startY 11.7, startZ 2.45, rotation 22.5) for them and (8.25, 6, 0) for the plain one.
  Every one of those numbers was measured in game; the table is in the port document.
- **The preset field completes on Tab.** It suggested ids and Enter filled one in, but the ids are
  long and the list could not be walked. Tab takes the next candidate and keeps walking on repeat,
  wrapping at the end, and typing by hand resets the walk — the behaviour vanilla's command
  suggestions have, which this widget was written to imitate.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-1.2

## Compatible MTR Version
YMTR

## Changes
- **The panel no longer floats off its block.** JCM 2.x's panel translate (`-0.128` on Z) was
  tuned against MTR 4's model; MTR 3's RV model is a mount rather than a screen — a base, two
  side plates running the block's full depth along its centre line, and a pole — so the panel sat
  0.148 blocks out and the gap was plain to see from the side. It is 0.134 now. The usable range
  is narrow and was measured in game: 0.120 puts the panel inside the mount and the plates cut
  through it, 0.148 clears it but reads as a floating board.
- **Presets that draw a row as several elements no longer creep outward line by line.** The depth
  step between draw calls was `-0.1` script units — 500 times JCM 2.x's `0.0002`, and 5 times it
  in blocks — so forty calls accumulated 4 cm and `met_bus_stop` and the Japanese-style packs
  came out as a staircase. The large step was a workaround for the flicker the old sorted batch
  produced; quads are drawn one at a time now, in call order, so nothing competes for depth and
  the step is back to JCM 2.x's value.

# Yomi's Joban Client Mod 1.2.12-JSPIDS-1.1

Version numbers follow the upstream release this fork is built on, with the JS-PIDS port revision
appended (`1.2.12-JSPIDS-1.0` was the first published port build).

## Compatible MTR Version
YMTR

## Changes
- **Overlays drawn by a PIDS preset no longer vanish.** Badges, platform circles and weather
  icons were invisible on some packs while the panel background came out fine. Script quads are
  now drawn through the engine's own buffer and uploaded immediately, so a preset's calls reach
  the screen in the order it made them — which is what JCM 2.x's queued renderer gives them.
  This matters on any pack where another mod takes over entity rendering: *Accelerated
  Rendering* mixes into `MultiBufferSource.BufferSource.getBuffer`, so the quads never reached
  a buffer that could be flushed in order, and only the background survived.
- **A panel's own station is now the one it stands at, not an arbitrary one.** The block
  entity's platform list is a `Set`, so taking its first element picked a platform at the other
  end of the line: HKR's route-map strip drew the line's opening stations and the platform
  number and "arriving here" wording followed the wrong station. MTR's rule — the platform
  closest to the block — is used now.
- **MTR 4-shaped fields are exposed on the script objects**, because Rhino hands back a bound
  method for a property lookup on a method: `pids.station().name` returned a function object
  rather than the name. `Station.name/id/zone`, a route stop's `station` and `route`, and
  `route.name` are fields now, with the accessor methods kept.
- **`departureTime()` counts from the arrival plus the platform's dwell time**, in MTR's
  half-seconds. It used to return the arrival time, so HKR's door-closing display could never
  appear; with a 20-second stop it now shows for the last ten seconds before departure.

# Yomi's Joban Client Mod 1.2.4 has been released

## Compatible MTR Version
YMTR

## Changes
- Compatible with YMTR.
- Sound Looper can play net audio.

# Minecraft 1.20

## Downloads
[Github release](https://github.com/yomi-china/Yomis-Joban-Client-Mod/releases)
