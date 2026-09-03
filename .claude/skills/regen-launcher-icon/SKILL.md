---
name: regen-launcher-icon
description: Regenerate the adaptive launcher icon foreground + legacy mipmaps from the source mascot PNG via tools/generate_launcher_icon.py. Wraps the canonical params from docs/knowledge/launcher-icon-generation.md (tolerance=28, crop-fraction=0.7474 → 下沿 1550px). Use when user says 重新生成 launcher 图标 / 调启动图标 / 换图标素材 / 改图标下沿. User-only — invoke via /regen-launcher-icon because it rewrites res/drawable-nodpi/ic_launcher_foreground.png + 5 mipmap densities.
disable-model-invocation: true
---

# Regen Launcher Icon (IceSpiritAI_Vision)

Regenerates the launcher icon (adaptive foreground + 5 legacy mipmap densities)
by running `tools/generate_launcher_icon.py` with the canonical parameters
from [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md).

## When to invoke

- User says 重新生成 launcher 图标 / 调启动图标 / 换图标素材 / 改图标下沿
- Mascot source artwork changes (e.g. male → female)
- Compositon needs to zoom in/out (change `--crop-fraction`)
- Adaptive foreground shows a transparent edge artifact on the launcher
- Launcher icon rebrand (background color change)

## Hard constraints

- **Top must stay at y=0**. The mascot's head is at the top of the source art;
  cropping from anywhere else crops off the head.
- **Bottom edge is the only thing to change** (composition tuning). Map:
  `--crop-fraction = 下沿像素 / 2074`.
- **Tolerance=28** is the canonical near-white flood threshold for this source
  (saturated shirt + dark outline). Don't lower without testing — lower tol
  eats more shirt, higher tol eats more background.
- Adaptive foreground MUST be transparent (no `#FDFDFD` rectangle around the
  figure). Composites onto `@color/launcher_icon_bg = #FDFDFD` (matching
  legacy fallback).
- Don't rename output stems (`ic_launcher`, `ic_launcher_round`,
  `ic_launcher_foreground`) — `AndroidManifest.xml` references them by name.

## Canonical invocation

### 1. Run with current canonical params

```bash
python tools/generate_launcher_icon.py \
    "D:\GitHub\冰灵图标\冰灵（男）.png" \
    --tolerance 28 \
    --crop-fraction 0.7474
```

| Param | Value | Why |
|---|---|---|
| `source` | `冰灵图标/冰灵（男）.png` (1280×2074) | Current mascot art |
| `--tolerance 28` | border-flood near-white threshold | Empirically tuned for this source |
| `--crop-fraction 0.7474` | keeps top 1550px (= 0.7474 × 2074) | Current composition (head + upper body) |

### 2. Param tuning (only when changing composition)

Pixel ↔ fraction map (source is 2074px tall):

| 下沿(px) | `--crop-fraction` |
|---|---|
| 1280 (initial square) | 0.617 |
| 1493 (1280 × 7/6) | 0.72 |
| **1550 (current)** | **0.7474** |
| 1626 | 0.784 |

Changing the lower bound (zooming in or out) is the only way to tune
composition — do not shift the top edge (head position is fixed at y=0).

### 3. Acceptance checklist

```bash
ls -la app/src/main/res/drawable-nodpi/ic_launcher_foreground.png
ls app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
ls app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png
```

Verify:
- `ic_launcher_foreground.png` is RGBA (alpha channel exists)
- Foreground shows the head + upper body, centered in 432×432 canvas
- Adaptive XML unchanged: `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`
- 5 mipmap densities all generated (48/72/96/144/192 px)

### 4. Build + on-device check

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify on nova 6 (or any device):
- App drawer icon shows new artwork
- Adaptive icon mask (circle/square/rounded-square per launcher) clips correctly
- No white rectangle halo around the figure (would indicate tolerance too high)
- No punched-out area in shirt / face (would indicate tolerance too low)

Resource changes don't depend on `modelProfile` — `shell` is faster.

## Required reads (before invoking)

- [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md) — full param rationale + pixel ↔ fraction table
- [`tools/generate_launcher_icon.py`](tools/generate_launcher_icon.py) — script source (flood-fill implementation)
- [`app/src/main/res/values/colors.xml`](app/src/main/res/values/colors.xml) — `launcher_icon_bg = #FDFDFD`
- [`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) — adaptive icon XML
- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — `android:icon` reference

## Required writes

- `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` (replaced)
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (replaced)
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png` (replaced)

## Output

```markdown
# Launcher Icon Regen Report

## Input
- source: `D:\GitHub\冰灵图标\冰灵（男）.png`
- params: tolerance=28, crop-fraction=0.7474 (下沿 1550px)

## Outputs
- drawable-nodpi/ic_launcher_foreground.png (size + KB)
- mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png (5 buckets)

## Acceptance
- [ ] foreground RGBA with transparency
- [ ] adaptive XML unchanged
- [ ] on-device render matches composition expectation
- [ ] no white halo (tolerance too high) or face punch-out (tolerance too low)
```

## Companion skill

`regen-mascot` covers the in-app mascot PNG (different script, different source,
different acceptance criteria). Don't conflate — launcher uses flood-fill on
saturated shirt (works fine), mascot needs `rembg isnet` because dark surface
exposes colour-heuristic failure modes.

## Hard rules (CLAUDE.md + memory)

- All commits authored by `AlexMultiAgent`.
- Never add `Co-Authored-By: Claude ...` trailer.
- Sensitive files (`gradle.token.properties`, `local.properties`, `~/.gradle/gradle.properties`) must not be staged.
- Use explicit file paths in `git add` (PreToolUse hook blocks `-A` / `.` / `*`).
- Don't touch `app/libs/*.aar`.