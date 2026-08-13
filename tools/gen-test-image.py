#!/usr/bin/env python3
"""Generate the deterministic OCR smoke-test fixture.

Output: app/src/androidTest/assets/test.png

The fixture deliberately contains Chinese advertising copy with two
superlative terms ("国家级", "最佳品牌") that Phase 1's rules engine is
meant to flag. That keeps a single fixture useful for both the SDK smoke
test (does OCR return text at all?) and later rules-engine tests.

Regenerate with:  python tools/gen-test-image.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

LINES = ["国家级 优质产品", "最佳品牌 全国销量第一"]
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simsun.ttc",
]
WIDTH, HEIGHT, FONT_SIZE = 520, 170, 40


def load_font() -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, FONT_SIZE)
    raise SystemExit(f"No CJK font found; tried: {FONT_CANDIDATES}")


def main() -> None:
    out = (
        Path(__file__).resolve().parent.parent
        / "app/src/androidTest/assets/test.png"
    )
    out.parent.mkdir(parents=True, exist_ok=True)

    font = load_font()
    img = Image.new("RGB", (WIDTH, HEIGHT), "white")
    draw = ImageDraw.Draw(img)

    for i, line in enumerate(LINES):
        draw.text((30, 28 + i * 62), line, fill="black", font=font)

    # Palette-quantize: text-on-white needs very few colors, so this keeps
    # the committed fixture small without touching glyph legibility.
    img.convert("P", palette=Image.ADAPTIVE, colors=16).save(out, optimize=True)
    print(f"wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
