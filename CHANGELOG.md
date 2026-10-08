# Yomi's Joban Client Mod 1.2.12-JSPIDS-2.0

## Compatible MTR Version
MTR

## Why the version jumps to 2.0

1.5 could draw every preset we had. What it could not do was run a preset written against the
scripting documentation rather than against the presets we happened to test. This release closes
that gap: the scripting surface is now the documented Common APIs, and two real packs that failed
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
