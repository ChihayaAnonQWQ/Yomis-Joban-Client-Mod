# Pixelation and dot-matrix boards — a guide for resource pack authors

[中文](pixelation-guide.zh.md)

This is for the person writing the `.js` preset and the JSON that goes with it. It says where to put
the pixelation settings, what each number does, and which numbers give which look.

---

## 1. What the mod does with your panel

When a preset asks for pixelation, the mod stops drawing straight onto the block and instead:

1. renders the whole panel into a small offscreen image,
2. magnifies that image onto the panel.

The first step is where the resolution comes from, the second is where the dots come from. They are
**two different numbers**, because a real board has two different things: a picture, and a grid of
lamps. Each lamp shows one value, taken from everything behind it.

| | What it decides | Keys |
|---|---|---|
| **Content resolution** | how finely the panel is drawn — whether text is smooth or blocky | `pixelScale`, `pixelResolution` |
| **Lamp grid** | how many dots you actually see, and how big each one is | `pixelDots` |
| **Dot shape** | whether a dot is a square or a round lamp | `pixelShape` |

Leave all of them out and nothing changes: the panel is drawn straight onto the block, exactly as it
was before this feature existed.

---

## 2. Where to put it

The file the mod already reads for your presets:

```
assets/jsblock/joban_custom_resources.json
```

There are two places inside it, and you can use either or both.

### 2a. Once for the whole pack — top level

```json
{
  "pixelation": {
    "enabled": true,
    "scale": 4,
    "resolution": [272, 152],
    "dots": [68, 38],
    "shape": "circle"
  },
  "pids_images": [ ... ]
}
```

| Key | Values | Meaning |
|---|---|---|
| `enabled` | `true` / `false` | whether you want any of your presets pixelated. This is the one thing a pack could not say before: a preset carrying a `pixelScale` used to be pixelated whether you liked it or not |
| `scale` | `2` … `8` | the default divisor. `3` draws the panel a third as wide. A divisor only lands on grids that divide the canvas evenly |
| `resolution` | `96`, `[96, 54]`, `{"width": 96, "height": 54}` | the default grid in dots. More precise than `scale` |
| `dots` | same three forms | the lamp grid, when it should be coarser than the picture |
| `shape` | `"square"` / `"circle"` | `square` is an LCD, `circle` is a dot-matrix board |

### 2b. Per preset — inside a `pids_images` entry

```json
"pids_images": [
  {
    "id": "my_crt_board",
    "name": "CRT PIDS",
    "pixelScale": 4,
    "pixelResolution": [272, 152],
    "pixelDots": [68, 38],
    "pixelShape": "circle",
    "scriptFiles": ["mypack:pids/script/my_board.js"]
  }
]
```

Per-preset settings win over the pack-level block. The `id` here is the id players and the client
config use, so it is worth picking something readable.

### 2c. The player can override all of it

In `config/jsclient.json`, keyed by preset id:

```json
{
  "pixelScaleByPreset":      { "my_crt_board": 4 },
  "pixelResolutionByPreset": { "my_crt_board": [272, 152] },
  "pixelDotsByPreset":       { "my_crt_board": [68, 38] },
  "pixelShapeByPreset":      { "my_crt_board": "circle" },
  "pixelShapeDefault":       "square"
}
```

**The player always wins**, including when their entry says `1` — that is how they refuse a pack that
declares pixelation for a board they would rather see sharp. Ship what your artwork was drawn for and
let them disagree; there is nothing you can do to prevent it and nothing you need to.

---

## 3. The proportions are always the canvas's

The magnified image is stretched across the whole panel, so a grid whose proportions differ from the
canvas's comes out **squashed**. To make that impossible, the mod reads your **width as the intent**
and recomputes the height from the canvas. `"pixelResolution": 96` on a 136×76 canvas becomes 96×54;
`[96, 50]` also becomes 96×54, with a warning in the log saying so.

The three canvases, and the widths that land on their exact proportions:

| Canvas | Which PIDS | Ratio | Widths that land exactly |
|---|---|---|---|
| **136 × 76** | RV PIDS, both SIL slants, the PIDS Projector | **34 : 19** | 34, 68, 102, 136, … |
| **133 × 72** | LCD PIDS | **133 : 72** | 133 (and only 133 — the pair is already reduced) |
| **186 × 60** | 1A PIDS | **31 : 10** | 31, 62, 93, 124, 155, 186 |

Any other width still works; the height is rounded to the nearest dot, which is at most half a dot of
error. If you want the grid to line up exactly with the script's own units, stay on those multiples.

---

## 4. Choosing the numbers

### How big is a dot on screen?

This is the part that decides whether your board reads as a dot matrix at all. A panel is about two
blocks wide, which is roughly **200 screen pixels** at a normal viewing distance on a 1080p screen. So:

| Dots across | Screen pixels per dot | How it looks |
|---|---|---|
| 22 | ≈ 9 | very chunky blocks |
| 34 | ≈ 6 | clear dots |
| **68** | **≈ 3** | **what a real metro LED board looks like** |
| 96 | ≈ 2 | fine dots, still countable |
| 136 | ≈ 1.5 | just resolvable as dots |
| 1360 | ≈ 0.15 | **not visible at all** — the dots average into a flat tint |

A dot smaller than about two screen pixels cannot be seen as a dot; asking for a grid that fine is the
same as asking for no dots. This is not a limitation of the mod — a 4K monitor has no visible pixels
either, for the same reason.

### So which do I want?

| You want | Write |
|---|---|
| **A real dot-matrix board** — fine picture, visible lamps | `"pixelResolution": [272, 152], "pixelDots": [68, 38], "pixelShape": "circle"` |
| The same, chunkier | `"pixelDots": [34, 19]` |
| A blocky LCD, content and pixels the same size | `"pixelScale": 4, "pixelShape": "square"` |
| Blocky content with round lamps — the "each lamp is a pixel" board | `"pixelResolution": [68, 38], "pixelShape": "circle"` |
| Smooth text, no dots at all | `"pixelResolution": [272, 152]` (or larger, e.g. `[1360, 760]`) |
| Nothing at all — the default | write nothing |

### Worked example

Your artwork is a picture drawn for a 68×38 board, but you want its text to look like real text rather
than 38 rows of blocks. Draw the script as usual — it still thinks in canvas units — and declare:

```json
{
  "id": "my_board",
  "pixelResolution": [272, 152],
  "pixelDots": [68, 38],
  "pixelShape": "circle"
}
```

The panel is rendered at 272×152, each lamp then averages the 4×4 block of pixels behind it, and the
circle mask turns each lamp into a round dot. That is what a greyscale LED board does.

---

## 5. What it costs

- One offscreen framebuffer per (canvas size, grid) pair, shared between all panels that ask for the
  same combination and cached until the resource packs reload. A 272×152 target is 165 KB.
- One extra quad per panel for the dot mask, and — only when the grid is finer than the canvas —
  a mipmap pass so that the minified image does not sparkle.
- Nothing at all for presets that declare none of this.

---

## 6. Things that will bite

**A preset with a transparent canvas and `"circle"`.** The gaps between dots are painted wherever the
mask is, and there is no per-pixel alpha to test against. Presets that paint a full background (which
is nearly all of them — a `Texture.create("Background").size(pids.width, pids.height)` first line) are
fine; one that leaves part of its canvas empty gets a speckled black grid there. Use `"square"` for
those, or paint a background.

**Older versions of the mod ignore all of it.** Unknown keys in `joban_custom_resources.json` are
skipped, so a pack that declares pixelation still loads on a build that has never heard of it. That is
deliberate: this is a picture setting, not a script API, and it must not fail a pack on an older jar.

**Check the log.** Each preset that pixelates says so once:

```
[PIDS pixelation] preset=my_board scale=1 (declared grid 272x152) shape=circle dots=68x38
                  target=272x152 drawCalls=11 centrePixel=18,149,149,255 glError=0x0
```

`dots=` is what you will actually see, `target=` is what the content was rendered at, and
`centrePixel` tells you the offscreen pass drew something. If a grid you typed is not the grid in the
log, the log also says why:

```
[PIDS pixelation] A resolution of 96x50 does not keep the canvas's 136x76 proportions;
                  using 96x54 so the panel is not stretched. Reported once.
```

**A script that throws still gets pixelated.** The offscreen pass retries once with placeholder
arrivals, the same recovery the direct path applies, and says so in the log. If your preset throws on
its first pass every frame, fix the preset — the retry is a safety net, not a substitute.

## 7. Express and local (快慢车) — where a preset gets it

A preset that wants to announce "this train is a rapid service" has to get that from somewhere, and
**MTR has no field for it**: neither MTR 3 nor MTR 4 has an express-versus-local flag, and MTR 4 has no
route type either. What the packs that care actually do is put the service type in the route's
**number** and match keywords against it. HKR's `hkr_pids_default.js` is the clearest example:

```js
let rawRoute = train.routeNumber();                                     // the route's number, as text
let routeNumText = String(rawRoute).trim();
let blockColor = getColorByKeyword(routeNumText, train.routeColor());   // keyword -> badge colour

function getColorByKeyword(text, defaultColor) {
    if (text.includes("区間快速") || text.includes("Semi-Rapid"))      return 0x009944;
    if (text.includes("特急")     || text.includes("Limited Express")) return 0xE60012;
    if (text.includes("急行")     || text.includes("Express"))         return 0xEE7800;
    if (text.includes("快速")     || text.includes("Rapid"))           return 0x0067C4;
    if (text.includes("各停") || text.includes("普通") || text.includes("Local")) return 0x777777;
    return defaultColor;
}
```

The same table appears word for word in `kamino_jp_pids.js` (Japanese_PIDS v1.5), also fed from
`routeNumber()`. Note the **order**: `区間快速` contains `快速`, and `Limited Express` contains
`Express`, so the longer phrase has to be tested first. Reorder that table and the colours change.

### What MTR 3 gives you

MTR 3 does have a route number — it is `Route.lightRailRouteNumber`, free text, and it is gated behind
a checkbox that MTR's own language file labels **"Has Route Number"** (the field behind it is called
`isLightRailRoute`). It is synchronised to the client, our wrapper reads it, and on a route with no
number the preset falls back to the car count — which is the "6卡" badge you see on an unconfigured
line.

So, for a board to show 快慢车:

1. **Give each service its own route** — 快速 and 普通 as two routes, not one route running both.
2. Tick **Has Route Number** on each, and type the service word in it.
3. Nothing else: no pack edit, no mod setting.

### The granularity is the route, not the departure

The number belongs to the route, and the PIDS reads it per arrival through that arrival's route id. So:

| How you model it | What the board shows |
|---|---|
| One route per service type | ✓ each arrival shows its own label |
| One route running both | ✗ every train on it shows the same label — a local arriving will say 快速 |

There is no per-departure field in MTR 3 or MTR 4 to say "this particular train is the fast one", so
this is a modelling rule rather than a mod limitation.

### Changing it later

The number is an ordinary route property: edit it in the route dashboard at any time. The change reaches
clients on the next tick (`ClientData.ROUTES` is rebuilt every tick), the PIDS looks it up without
caching, so **the board is right on the next frame** — no relog, no restart. Clearing the number puts
the badge back to the car count.
