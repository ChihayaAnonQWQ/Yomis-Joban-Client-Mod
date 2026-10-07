# Yomi's Joban Client Mod

An unofficial version of Joban Client Mod.

Only 1.20(.1).

Yomi's Joban Client Mod (Abbreviated as YJCM) is an addon based on [Minecraft Transit Railway](https://github.com/jonafanho/Minecraft-Transit-Railway) Mod, adding various blocks from the Hong Kong MTR and utility blocks that will greatly improve your world.

Some of the blocks this mod adds including custom signal light, fare saver machine, the Railway Vision PIDS and more! 

![](https://user-images.githubusercontent.com/40461728/187031355-e71be327-e520-4add-aaba-dc9b6421fb44.png)

> [!IMPORTANT]
> ## License
> This repository contains code from **two different sources**:
>
> | Part | License | Copyright |
> |---|---|---|
> | JCM (Joban Client Mod) code | MIT (see [LICENSE_JCM](LICENSE_JCM)) | AmberFrost, StrikeSNC, AozoraSky |
> | YJCM original code (written by Yomi) | All Rights Reserved (see [LICENSE](LICENSE)) | Yomi (Yomi_307) |
>
> - The JCM portion may be used, modified, and distributed under the MIT License.
> - The YJCM-specific code is **NOT open source**. Copying, redistributing, or modifying it without permission is prohibited.
> - If you fork this repository, you must keep both license files and this notice.

## What this fork changes

The branch **`feat/jcm-pids-components`** ports **JCM 2.x / MTR 4's PIDS system** onto
**YJCM for MTR 3** (Minecraft 1.20.1, YMTR 3.6.3) — and then fixes some of the problems
that putting those panels into a world turned up. A PIDS preset either lays out correctly
on a real block or it does not, and only the game can say which.

Full write-up, in Chinese, with the decompiled evidence and the in-game logs:
**[MTR3-PIDS-PORT.md](MTR3-PIDS-PORT.md)**. This section is the summary.

### Added

| | |
|---|---|
| Component layouts | `com.jsblock.pids` — 11 components; a resource pack declares the layout in JSON (`components`) |
| JavaScript presets | `com.jsblock.script` — embedded Rhino 1.7.15; a JCM 2.x `.js` preset runs **unmodified** (`scriptFiles`) |
| Script sandbox | class-access allow-list, a warning screen before switching it off, player-facing failure notices, a debug overlay |
| 1A PIDS presets | `jsblock:pids_1a` gets the preset storage, auto-switch and config screen the LCD/RV PIDS already had |
| Textures | JCM 2.x's PIDS artwork (`weather_*`, `plat_circle`, `rv_default`, `black`) |
| Checks | `tools/run-pids-check.ps1` — three headless checks that run real presets through the real engine |
| Diagnostics | `-Djsblock.pids.trace=true` logs every draw call a preset issues, with type, depth, colour and text |
| Docs | `MTR3-PIDS-PORT.md` |

A preset that declares neither `components` nor `scriptFiles` keeps the old hard-coded
render path, unchanged.

### Upstream defects fixed — the fork does not build without these

| | |
|---|---|
| `ForgeConfig.java` missing | referenced by `JobanForge`, never committed upstream |
| Gradle 8 | `classifier` was removed; now `archiveClassifier` |
| `font` vs `fonts` | the preset reader used `fonts`, every renderer used `font`, so the key was silently dropped |

### Rendering defects found in game

| Symptom | Cause |
|---|---|
| Weather icon as a white square; the panel background flickering | script textures went through the opaque render layer; JCM 2.x uses a translucent one |
| Route-number / car-count badge with no background, platform circle showing only its number, route-map bar and station dots missing | `.color()` takes a six-digit RGB and MTR 3 reads the alpha straight out of it; JCM 2.x adds `ARGB_BLACK` first |
| Text growing on every marquee pass, then scrolling outside the panel | the scroll was implemented as leading spaces, so it took part in the width measurement |
| Whole panel ~40% off to the left | the panel origin was derived from the panel size instead of copied from JCM 2.x's hard-coded literals |
| Every two-block PIDS showing on one side only | one half was skipped; JCM 2.x draws from both, one face each |
| A 1A panel painting twice, or not at all | `pids.isKeyBlock()` was hard-coded, so the preset could not tell the halves apart |
| CJK drawn at half the size its own box reserved | the layout and the draw used two different CJK multipliers |

### Data defects found in game

| Symptom | Cause |
|---|---|
| Empty rows filled with "not in service", each with a permanent "1 min" that flips between languages | `arrivals().get(i)` returned a placeholder object past the end of the list instead of `null`, so a preset's own null check never fired |
| `pids.station()` null on every panel that auto-detects its platform | the lookup was handed the `null` world its guard rejected — which also made the route map always start at the first stop |
| `Route.getDestination` returning nothing on most routes | MTR 3 only reads per-stop custom destinations; MTR 4 falls back to the terminus |

A preset that indexes past the end without checking now throws, as it does in JCM 2.x —
and the engine recovers the frame by retrying it once with a placeholder, because a
resource pack is not something the mod can edit. The throw stays in the log; the player
gets one line, and gets the red error only when the retry could not save the panel.

### Config screen

The preset suggestion list was drawn under its text field and covered the rows below
while those rows drew their labels on top of it. It is drawn beside the field now, with
a background. Both PIDS config screens share the widget, so both are fixed.

### Building MTR 3 against this branch

MTR's development jars (`MTR-common-1.20-*-dev.jar`) are 404, so the build uses a
Mojang-mapped MTR 3 jar instead:

```
~/.gradle/caches/forge_gradle/deobf_dependencies/maven/modrinth/ymtr/
    1.20.1-3.6.3_mapped_official_1.20.1/ymtr-1.20.1-3.6.3_mapped_official_1.20.1.jar
```

Copy it to `checkouts/1.20/mtr-common.jar` (and `mtr-fabric.jar` / `mtr-forge.jar`;
the three are identical). `checkouts/` is git-ignored.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
gradle build --no-daemon --console=plain --max-workers=1        # -> build/MTR-YJCM-1.20-*.jar
.\tools\run-pids-check.ps1                                       # headless checks, non-zero on failure
```

A long stack trace during configuration is `build.gradle`'s `setupFiles` catch branch
printing a failed `Minecraft-Mappings` download; it is not a build failure.

## FAQ & Support
### Why does my game crash?
There's a variety of reasons, one of the main reasons is that <b>you're using the wrong version of the MTR Mod</b>.  
Version labeled as <u>Pre-release</u> should only be used with a preview version of MTR Mod [in their Discord](https://discord.gg/hvddbya8rh).  
If you're unable to download the preview version of MTR, please download the versions labeled as <u>Release</u>.

### The game still crashes / I want to report a bug / I want to give suggestions
Most of the support are done in our Discord for easier communication, please join our [Discord Server](https://discord.gg/FNc2rgWmP2) here.

### I want to know more!
We have documented most parts of the mod in our [wiki](https://www.joban.tk/wiki/JCM:Joban_Client_Mod).

## Setup

1. Clone this repository
2. Sync the Gradle project
3. On First run:
   1. Sync the Gradle Project
   2. After finishing, Sync the Gradle Project again
   3. Restart IntelliJ IDEA

## Updating MTR Mod
MTR Mod is a required dependencies of Joban Client Mod.

To update MTR Mod to the latest version in your dev environment, run the `setupLibrary` task.

## Cross version development
We now use [Manifold](https://github.com/manifold-systems/manifold), which also supplies a preprocessor to conditionally apply java code.

A `MC_VERSION` is passed to the preprocessor, and it will look something like `11904`:  
- The 1st digit is the major version (1)
- The 2nd - 3rd digit is the main version (19 for 1.19)
- The 3nd - 4th digit is the minor version (04 for 1.19.4)

For example, if you want to apply code for 1.19.3 and above, but not anything else:
```
   #if MC_VERSION >= "11903"
      LOGGER.info("This line will appear in 1.19.3 and above")
   #else
      LOGGER.info("This line will appear in 1.19.2 or below")
   #endif
```

## Troubleshooting
### Command line is too long. Shorten the command line and rerun.
Enable Shorten Command Line (Edit Configuration)
<img src=https://i.imgur.com/1XKc6ts.png>

### Modules generated_XXXXXX and jsblock export package com.jsblock to module architectury
<b>Currently only Forge 1.16.5 works</b>

### Everything goes wrong, error after error
1. Delete `.gradle` folder in your user account folder
2. <b>[IMPORTANT]</b> commit and push any uncommited files
3. Delete the entire Joban Client Mod folder
4. Re-clone the project
