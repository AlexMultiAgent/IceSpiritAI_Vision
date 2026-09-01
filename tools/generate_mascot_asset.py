#!/usr/bin/env python3
"""Cut the IceSpirit mascot out of a white backdrop and emit UI-ready PNGs.

Companion to `generate_launcher_icon.py`. That script produces launcher icons;
this one produces *in-app* artwork (the Home preview placeholder and any future
avatar), which has to sit on the dark `#11212C` surface without an off-white
rectangle around it.

Two crops come out of one source:

  full  whole figure, tight-cropped to the content bounding box
  bust  head + shoulders, square, sized so a circular clip keeps the whole face

Matting engine, in order of preference:

  isnet   `rembg` + `isnet-general-use` (default). A saliency network reads the
          silhouette from structure, so it survives the two traps below.
  chroma  dependency-free fallback: flood fill seeded from the image border over
          pixels that are bright AND colour-neutral.

Why the fallback is only a fallback. Two colour heuristics were tried and both
fail on this artwork, and the failure modes are not obvious until you composite
onto dark:

  * `|rgb - white| <= tol` flood fill leaks. The pale blue placket is within ~50
    levels of white and the glasses lens has a near-white reflection band, so at
    tol 30 the fill walks through the lens and punches a black hole in the cheek;
    at any tolerance it eats the right placket.
  * Thresholding on channel spread (chroma) instead of distance-to-white fixes
    the lens - backdrop spread is 2, placket spread is 47 - but the collar has a
    near-white specular highlight along its top edge, which is a low-chroma
    channel straight into the leaf, so the cut comes out serrated.
  * Morphological closing to bridge that highlight cannot tell the collar seam
    from the genuine gap between the legs; at bridge=20 it filled the leg gap
    solid.

The launcher icon gets away with a flood fill because its source has a saturated
shirt with a dark outline AND it is re-composited onto near-white `#FDFDFD`, so
residual error is invisible. On a dark surface it is not.

After the mask is built it is hole-filled (an enclosed sparkle or badge must
never become a hole), lightly blurred to erase the network's 1024-px staircase,
and the edge band is un-mixed against the known backdrop
(`subject = (observed - (1-a)*bg) / a`) to kill the pale JPEG fringe. The un-mix
is confined to the edge band on purpose: dividing a mis-masked *interior* pixel
by a small alpha is what turned the earlier lens leak into a black blob instead
of a harmless hole.

Example:
    pip install rembg onnxruntime        # optional; enables the isnet engine
    python tools/generate_mascot_asset.py "冰灵（男）形象/戴智能眼镜.jpg" \
        --prefix mascot_glasses --max-dim 720
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

REPO_ROOT = Path(__file__).resolve().parent.parent


def load_rgb(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def detect_backdrop(rgb: np.ndarray) -> tuple[int, ...]:
    """Median colour of the 8-px border ring — the source's backdrop."""
    ring = np.concatenate([rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3),
                           rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)])
    return tuple(int(c) for c in np.median(ring, axis=0))


def matte_isnet(img: Image.Image, model: str) -> np.ndarray:
    """Alpha in [0,1] from a rembg saliency model. Raises if rembg is absent."""
    from rembg import new_session, remove  # imported lazily: optional dependency

    raw = remove(img, session=new_session(model), only_mask=True)
    return np.asarray(raw, dtype=np.float64) / 255.0


def matte_chroma(rgb: np.ndarray, chroma_tol: int, bright_min: int) -> np.ndarray:
    """Alpha from a border-seeded flood over bright, colour-neutral pixels."""
    chroma = rgb.max(axis=2) - rgb.min(axis=2)
    candidate = (chroma <= chroma_tol) & (rgb.min(axis=2) >= bright_min)
    labels, _ = ndimage.label(candidate)
    ids = np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))
    return (~np.isin(labels, ids[ids != 0])).astype(np.float64)


def build_alpha(img: Image.Image, rgb: np.ndarray, args: argparse.Namespace) -> np.ndarray:
    engine = args.engine
    alpha = None
    if engine in ("auto", "isnet"):
        try:
            alpha = matte_isnet(img, args.model)
            print(f"[mascot] engine=isnet/{args.model}")
        except Exception as exc:  # rembg not installed, model download blocked
            if engine == "isnet":
                raise
            print(f"[mascot] rembg unavailable ({type(exc).__name__}: {str(exc)[:90]})")
    if alpha is None:
        alpha = matte_chroma(rgb, args.chroma_tol, args.bright_min)
        print("[mascot] engine=chroma (border flood fill)")

    # An enclosed bright patch — eye sparkle, badge, shirt highlight — is content.
    solid = ndimage.binary_fill_holes(alpha > 0.5)
    alpha = np.where(solid, np.maximum(alpha, 0.98), np.minimum(alpha, 0.02))

    # Erase the network's low-res staircase before any scaling.
    if args.smooth > 0:
        blurred = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8), "L")
                             .filter(ImageFilter.GaussianBlur(radius=args.smooth)),
                             dtype=np.float64) / 255.0
        alpha = np.clip(blurred * 1.06 - 0.03, 0.0, 1.0)
    return np.clip(alpha, 0.0, 1.0)


def unmix(rgb: np.ndarray, alpha: np.ndarray, backdrop: tuple[int, ...]) -> np.ndarray:
    """Recover subject colour from `observed = a*subject + (1-a)*backdrop`."""
    rgb = rgb.astype(np.float64)
    bg = np.array(backdrop, dtype=np.float64)
    edge = (alpha > 0.001) & (alpha < 0.999)
    a = np.clip(alpha, args_floor(alpha), 1.0)[..., None]
    out = (rgb - (1.0 - a) * bg) / a
    return np.where(edge[..., None], out, rgb).clip(0, 255).astype(np.uint8)


def args_floor(alpha: np.ndarray) -> float:
    """Never divide by an alpha so small that a stray interior pixel explodes."""
    return 0.12


def content_bbox(alpha: np.ndarray) -> tuple[int, int, int, int]:
    ys, xs = np.where(alpha > 0.02)
    if xs.size == 0:
        raise SystemExit("[mascot] everything was classified as backdrop")
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def emit(name: str, matte: Image.Image, x0: int, y0: int, x1: int, y1: int,
         out_dir: Path, prefix: str, max_dim: int) -> None:
    h, w = matte.size[1], matte.size[0]
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(w, x1), min(h, y1)
    crop = matte.crop((x0, y0, x1, y1))
    scale = max_dim / max(crop.size)
    if scale < 1:
        crop = crop.resize((round(crop.width * scale), round(crop.height * scale)), Image.LANCZOS)
    target = out_dir / f"{prefix}_{name}.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    crop.save(target, optimize=True)
    print(f"[mascot] {target.name}: {crop.size[0]}x{crop.size[1]} "
          f"{target.stat().st_size / 1024:.0f} KB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                    formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path, help="mascot art on a white backdrop")
    parser.add_argument("--res", type=Path, default=REPO_ROOT / "app" / "src" / "main" / "res")
    parser.add_argument("--prefix", required=True,
                        help="resource stem, e.g. mascot_glasses -> mascot_glasses_bust.png")
    parser.add_argument("--max-dim", type=int, default=720, help="longest edge emitted, in px")
    parser.add_argument("--engine", choices=("auto", "isnet", "chroma"), default="auto")
    parser.add_argument("--model", default="isnet-general-use", help="rembg model name")
    parser.add_argument("--smooth", type=float, default=1.4,
                        help="mask blur radius in source px; hides the network staircase")
    parser.add_argument("--chroma-tol", type=int, default=14, help="chroma engine only")
    parser.add_argument("--bright-min", type=int, default=200, help="chroma engine only")
    parser.add_argument("--bust-fraction", type=float, default=0.42,
                        help="share of the content height the bust square covers")
    parser.add_argument("--bust-margin", type=float, default=1.12,
                        help="multiplier on the bust square so a circular clip loses nothing")
    parser.add_argument("--padding", type=int, default=12)
    args = parser.parse_args()

    if not 0 < args.bust_fraction <= 1:
        parser.error("--bust-fraction must be in (0, 1]")

    img = Image.open(args.source).convert("RGB")
    rgb = np.asarray(img).astype(np.int16)
    backdrop = detect_backdrop(rgb)
    alpha = build_alpha(img, rgb, args)
    colour = unmix(rgb, alpha, backdrop)
    print(f"[mascot] {args.source.name} {img.width}x{img.height} "
          f"backdrop=#{backdrop[0]:02X}{backdrop[1]:02X}{backdrop[2]:02X} "
          f"opaque {alpha.mean():.1%} of px")

    matte = Image.fromarray(np.dstack([colour, (alpha * 255).astype(np.uint8)]), "RGBA")
    x0, y0, x1, y1 = content_bbox(alpha)
    pad = max(0, args.padding)
    width, height = x1 - x0, y1 - y0
    cx = (x0 + x1) // 2
    print(f"[mascot] content bbox x {x0}..{x1} y {y0}..{y1} ({width}x{height})")

    out_dir = args.res / "drawable-nodpi"
    emit("full", matte, x0 - pad, y0 - pad, x1 + pad, y1 + pad, out_dir, args.prefix, args.max_dim)
    side = round(min(height * args.bust_fraction * args.bust_margin, img.height))
    bx0 = max(0, min(cx - side // 2, img.width - side))
    emit("bust", matte, bx0, y0, bx0 + side, y0 + side, out_dir, args.prefix, args.max_dim)


if __name__ == "__main__":
    main()