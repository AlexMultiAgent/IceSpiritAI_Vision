---
name: regen-mascot
description: Regenerate the in-app mascot PNG (transparent bust on dark surface) from the source artwork via tools/generate_mascot_asset.py. Wraps the canonical params from docs/knowledge/mascot-ui-asset.md + acceptance checklist. Use when user says 重新生成吉祥物 / 重做 mascot / mascot 重抠图 / 换 mascot 素材. User-only — invoke via /regen-mascot because it rewrites res/drawable-nodpi/mascot_glasses_bust.png.
disable-model-invocation: true
---

# Regen Mascot (IceSpiritAI_Vision)

Regenerates the in-app mascot PNG (transparent bust, dark-surface safe) by
running `tools/generate_mascot_asset.py` with the canonical parameters from
[`docs/knowledge/mascot-ui-asset.md`](docs/knowledge/mascot-ui-asset.md). This
skill exists because the script accepts 9 parameters and the **only** correct
combination for the current 戴智能眼镜 source is `isnet` engine + smooth=1.4 +
specific bust-fraction. The acceptance checklist (paste onto `#11212C`, 2-2.5x)
catches failure modes invisible on a white preview background.

## When to invoke

- User says 重新生成吉祥物 / 重做 mascot / mascot 重抠图 / 换 mascot 素材
- Source artwork changes (new mascot image, new pose)
- A previous mascot regen introduced edge artifacts (lens hole / leg-fill / collar zigzag)
- `IdleMascotSize` dp changes (need to bump `--max-dim = dp × 4`)

## Hard constraints

- **Engine must be `isnet`** (or `auto` with rembg available). Three colour
  heuristics were tried on this artwork and **all three fail** on dark surfaces
  (lens hole / leg-fill / collar zigzag — see [docs/knowledge/mascot-ui-asset.md](docs/knowledge/mascot-ui-asset.md) §4).
- **Acceptance requires pasting onto `#11212C` and viewing 2-2.5x**, not just
  the white-background preview. Failure modes are invisible on white.
- Don't commit `app/src/main/res/drawable-nodpi/mascot_glasses_bust.png`
  without verifying the acceptance checklist — false positives are common.
- The bust PNG is a `drawable-nodpi` (density-independent), so any profile
  build (`shell` / `ice_ocr_rules`) packs it.

## Canonical invocation

### 1. Verify rembg availability

```bash
python -c "import rembg, onnxruntime" 2>&1 || pip install rembg onnxruntime
```

First run downloads ~179 MB to `%USERPROFILE%\.rembg`. Subsequent runs use
the cache.

### 2. Run with canonical params

```bash
python tools/generate_mascot_asset.py \
    "冰灵（男）形象/戴智能眼镜.jpg" \
    --prefix mascot_glasses \
    --max-dim 480 \
    --engine auto \
    --model isnet-general-use \
    --smooth 1.4
```

| Param | Value | Why |
|---|---|---|
| `--prefix mascot_glasses` | matches existing `mascot_glasses_bust.png` stem | Don't rename without UI coord |
| `--max-dim 480` | 120dp × xxxhdpi (4x) | Per [docs §6](docs/knowledge/mascot-ui-asset.md); 720px wastes 260KB |
| `--engine auto` | tries isnet first, chroma fallback | Default; explicit makes intent visible |
| `--model isnet-general-use` | saliency network | Only model verified on this source |
| `--smooth 1.4` | mask blur in source px | Hides network staircase without eating hair strands |

### 3. Param tuning (only if acceptance fails)

| Symptom on `#11212C` | Param change |
|---|---|
| Right lens has black hole | rembg model broken → re-run, or try `--model birefnet-plus` |
| Collar zigzag / saw-tooth edge | bump `--smooth` (1.4 → 1.8, watch hair) |
| White fringe / halo at edge | bump `--smooth`, or check matte output separately |
| Bust too tight on face | `--bust-fraction 0.42 → 0.48` (more body) |
| Bust too loose | `--bust-fraction 0.42 → 0.36` |
| Image too small in `ImagePreview` | check `IdleMascotSize` in `ImagePreview.kt`, sync `--max-dim = dp × 4` |

### 4. Acceptance checklist (mandatory)

```bash
# 1. Composite onto the dark surface color
python -c "
from PIL import Image
mascot = Image.open('app/src/main/res/drawable-nodpi/mascot_glasses_bust.png')
canvas = Image.new('RGB', mascot.size, (17, 33, 44))  # #11212C
canvas.paste(mascot, (0, 0), mascot)
canvas.save('/tmp/mascot_on_dark.png')
print('size:', canvas.size)
"

# 2. Inspect /tmp/mascot_on_dark.png at 2-2.5x (use any image viewer).
#    MUST verify: right lens intact (no black hole),
#                  collar top edge smooth (no zigzag),
#                  white shirt placket solid (no perforation),
#                  between the legs truly transparent (leg gap not filled).
```

Any of those 4 areas failing → **do not commit**, re-run with adjusted params.

### 5. Verify resource pickup

```bash
grep -r 'mascot_glasses_bust' app/src/main/java/com/icespiritai/offline/ui/ 2>/dev/null
```

If `IdleMascotSize` doesn't match `--max-dim / 4`, fix the Kotlin constant
**or** adjust `--max-dim` accordingly (not both).

### 6. Build + on-device check (optional)

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Resource changes don't depend on `modelProfile` — `shell` build is faster.
On-device preview confirms the `ImagePreview` empty state renders correctly.

## Required reads (before invoking)

- [`docs/knowledge/mascot-ui-asset.md`](docs/knowledge/mascot-ui-asset.md) — full rationale for every param
- [`tools/generate_mascot_asset.py`](tools/generate_mascot_asset.py) — script source (8.0 docstring covers engine rationale)
- [`app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`](app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt) — `IdleMascotSize` constant

## Required writes

- `app/src/main/res/drawable-nodpi/mascot_glasses_bust.png` (replaced)

## Output

```markdown
# Mascot Regen Report

## Input
- source: `冰灵（男）形象/戴智能眼镜.jpg`
- engine: isnet-general-use (auto fallback to chroma if rembg unavailable)
- params: max-dim=480, smooth=1.4, bust-fraction=0.42

## Outputs
- `app/src/main/res/drawable-nodpi/mascot_glasses_bust.png` (size + KB)

## Acceptance
- [ ] right lens intact
- [ ] collar top edge smooth
- [ ] white shirt placket solid
- [ ] leg gap transparent

## Returns
- file path + size + KB
- param values used (so future runs are reproducible)
```

## Hard rules (CLAUDE.md + memory)

- All commits authored by `AlexMultiAgent`.
- Never add `Co-Authored-By: Claude ...` trailer.
- Sensitive files (`gradle.token.properties`, `local.properties`, `~/.gradle/gradle.properties`) must not be staged.
- Use explicit file paths in `git add` (PreToolUse hook blocks `-A` / `.` / `*`).
- Don't touch `app/libs/*.aar`.