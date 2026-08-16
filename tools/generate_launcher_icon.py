#!/usr/bin/env python3
"""Generate launcher icons for IceSpiritAI_Vision from the IceSpirit mascot PNG.

Input is a portrait illustration on a near-white background, e.g.
    D:\\GitHub\\冰灵图标\\冰灵（男）.png

Outputs (into app/src/main/res):
  - drawable-nodpi/ic_launcher_foreground.png   432x432 RGBA foreground layer
  - mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png + ic_launcher_round.png
    (legacy full-bleed fallbacks for API < 26)

The adaptive icon XML under mipmap-anydpi-v26/ and the launcher background
color are maintained separately; this script only produces the raster layers.

The background is removed with a flood fill seeded from the image border: only
the near-white region connected to the border becomes transparent, so light
areas *inside* the mascot are preserved.
"""

from __future__ import annotations

import argparse
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

FOREGROUND_SIZE = 432
# Adaptive icon canvas is 108dp; keep important content inside the 66dp safe
# circle. 66 / 108 * 432 = 264 px.
SAFE_ZONE_PX = 264
BACKGROUND = (253, 253, 253)  # matches the source's off-white backdrop

DENSITY_BUCKETS = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def load_rgb(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGB"))


def remove_border_background(rgb: np.ndarray, tolerance: int = 28) -> np.ndarray:
    """Return an alpha channel; border-connected near-white pixels become 0."""
    height, width = rgb.shape[:2]
    white = np.array([255, 255, 255], dtype=np.int16)
    near_white = np.abs(rgb.astype(np.int16) - white).max(axis=2) <= tolerance

    visited = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()
    for y in range(height):
        for x in (0, width - 1):
            if near_white[y, x]:
                visited[y, x] = True
                queue.append((y, x))
    for x in range(width):
        for y in (0, height - 1):
            if near_white[y, x] and not visited[y, x]:
                visited[y, x] = True
                queue.append((y, x))

    while queue:
        y, x = queue.popleft()
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if (
                0 <= ny < height
                and 0 <= nx < width
                and near_white[ny, nx]
                and not visited[ny, nx]
            ):
                visited[ny, nx] = True
                queue.append((ny, nx))

    return np.where(visited, 0, 255).astype(np.uint8)


def top_biased_crop(
    rgb: np.ndarray, alpha: np.ndarray, fraction: float
) -> tuple[np.ndarray, int]:
    """Crop from the top edge downward.

    The mascot's head is at the top of the art. `fraction` controls how much of
    the source height is kept: 1.0 shows the whole figure (smaller head in the
    final icon), smaller values zoom in on the head/upper body.

    Returns (cropped_rgb, top_y).
    """
    height, width = rgb.shape[:2]
    ys, xs = np.where(alpha > 0)
    top = int(ys.min())
    crop_height = max(1, int(height * fraction))
    y0 = max(0, top - int(crop_height * 0.05))
    y0 = min(y0, height - crop_height)
    return rgb[y0 : y0 + crop_height, 0:width], y0


def crop_alpha(
    crop: np.ndarray, full_alpha: np.ndarray, y0: int
) -> np.ndarray:
    return full_alpha[y0 : y0 + crop.shape[0], 0 : crop.shape[1]]


def save_foreground(crop: np.ndarray, alpha: np.ndarray, out: Path) -> None:
    crop_height, crop_width = crop.shape[:2]
    scale = min(SAFE_ZONE_PX / crop_width, SAFE_ZONE_PX / crop_height)
    target = (round(crop_width * scale), round(crop_height * scale))
    crop_img = Image.fromarray(crop).resize(target, Image.LANCZOS)
    alpha_img = Image.fromarray(alpha).resize(target, Image.LANCZOS)
    canvas = Image.new("RGBA", (FOREGROUND_SIZE, FOREGROUND_SIZE), (0, 0, 0, 0))
    canvas.paste(
        crop_img,
        ((FOREGROUND_SIZE - target[0]) // 2, (FOREGROUND_SIZE - target[1]) // 2),
        alpha_img,
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)


def save_legacy(foreground: Image.Image, res_base: Path) -> None:
    for density, size in DENSITY_BUCKETS.items():
        canvas = Image.new("RGB", (size, size), BACKGROUND)
        layer = foreground.convert("RGBA").resize((size, size), Image.LANCZOS)
        canvas.paste(layer, (0, 0), layer)
        bucket_dir = res_base / f"mipmap-{density}"
        bucket_dir.mkdir(parents=True, exist_ok=True)
        canvas.save(bucket_dir / "ic_launcher.png")
        canvas.save(bucket_dir / "ic_launcher_round.png")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="mascot PNG to convert")
    parser.add_argument(
        "--res",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res",
        help="app res directory",
    )
    parser.add_argument("--tolerance", type=int, default=28)
    parser.add_argument(
        "--crop-fraction",
        type=float,
        default=1.0,
        help="fraction of the source height kept, measured from the top (1.0 = full figure)",
    )
    args = parser.parse_args()

    if not 0 < args.crop_fraction <= 1:
        parser.error("--crop-fraction must be in (0, 1]")

    rgb = load_rgb(args.source)
    alpha = remove_border_background(rgb, args.tolerance)
    removed = int((alpha == 0).sum())
    print(
        f"[icon] {args.source.name}: removed {removed:,} background px "
        f"({removed / alpha.size:.1%})"
    )

    height, width = rgb.shape[:2]
    ys, xs = np.where(alpha > 0)
    print(
        f"[icon] content bbox: x {xs.min()}..{xs.max()}, "
        f"y {ys.min()}..{ys.max()} (image {width}x{height})"
    )

    crop, top = top_biased_crop(rgb, alpha, args.crop_fraction)
    crop_height = crop.shape[0]
    print(
        f"[icon] crop fraction={args.crop_fraction:.2f}: "
        f"x 0..{width}, y {top}..{top + crop_height}"
    )

    crop_alpha_channel = crop_alpha(crop, alpha, top)
    foreground_path = args.res / "drawable-nodpi" / "ic_launcher_foreground.png"
    save_foreground(crop, crop_alpha_channel, foreground_path)
    print(f"[icon] foreground -> {foreground_path}")

    foreground = Image.open(foreground_path)
    save_legacy(foreground, args.res)
    print("[icon] legacy mipmaps ->", ", ".join(DENSITY_BUCKETS))


if __name__ == "__main__":
    main()
