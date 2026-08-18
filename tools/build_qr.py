#!/usr/bin/env python3
"""Generate APP download QR codes (PNG + SVG) with a centered logo.

Mirrors `tools/build_qr.py` from IceSpiritAI_Chat; only `DEFAULT_URL`
and the inline URL-context comments differ. Re-running overwrites
prior outputs. Usage:
    python tools/build_qr.py [--url URL] [--size PIXELS]
"""
from __future__ import annotations

import argparse
import base64
import io
import sys
from pathlib import Path

import qrcode
from PIL import Image, ImageDraw
from qrcode.image.svg import SvgPathImage

REPO_ROOT = Path(__file__).resolve().parent.parent
# 2026-08-17 (mirror of IceSpiritAI_Chat/tools/build_qr.py): the QR code
# points at the direct APK download URL under the fixed tag `latest`
# (LAN-only Gitea). Tapping from a phone on the LAN downloads the
# latest `icespiritai-vision.apk` in one step — replacing the
# previous two-hop pattern of Gitea project page → release page →
# APK asset. The fixed tag + fixed asset name keep this URL stable
# across versions, so the QR image itself never needs to change
# for a new APK.
#
# The asset name `icespiritai-vision.apk` is set by
# `app/build.gradle.kts :: archiveVisionRelease` (the in-app update
# channel filename, separate from `app-release.apk` which is the
# permanently archived copy under `发布版历史存档/`).
DEFAULT_URL = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision.apk"
DEFAULT_OUT_DIR = REPO_ROOT / "docs" / "promo"
DEFAULT_LOGO = REPO_ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_foreground.png"
DEFAULT_SIZE = 1024
DARK_COLOR = "#0F1B2D"
LIGHT_COLOR = "#FFFFFF"
LOGO_RATIO = 0.28
WHITE_PAD_PX = 4


def crop_to_square(img):
    """Crop an RGBA image to non-transparent content, then pad to a square."""
    alpha = img.split()[-1]
    bbox = alpha.getbbox()
    if bbox is None:
        return img
    cropped = img.crop(bbox)
    w, h = cropped.size
    side = max(w, h)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    offset_x = (side - w) // 2
    offset_y = (side - h) // 2
    square.paste(cropped, (offset_x, offset_y))
    return square


def build_qr_matrix(url: str) -> qrcode.QRCode:
    """Build a fitted QR matrix with H correction and a four-module border."""
    qr = qrcode.QRCode(
        version=None, error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=10, border=4,
    )
    qr.add_data(url)
    qr.make(fit=True)
    return qr


def make_png(url: str, size: int, logo_path: Path, out_path: Path) -> None:
    """Render a square RGB PNG with a circular logo at its center."""
    qr = build_qr_matrix(url)
    img = qr.make_image(fill_color=DARK_COLOR, back_color=LIGHT_COLOR)
    img = img.get_image().convert("RGBA").resize(
        (size, size), Image.Resampling.NEAREST)

    logo_size = int(size * LOGO_RATIO)
    logo = Image.open(logo_path).convert("RGBA")
    logo = crop_to_square(logo)
    logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    logo_mask = Image.new("L", (logo_size, logo_size), 0)
    ImageDraw.Draw(logo_mask).ellipse((0, 0, logo_size - 1, logo_size - 1), fill=255)
    logo_circle = Image.new("RGBA", (logo_size, logo_size), (0, 0, 0, 0))
    logo_circle.paste(logo, (0, 0), logo_mask)

    white_size = logo_size + 2 * WHITE_PAD_PX
    white_circle = Image.new("RGBA", (white_size, white_size), (0, 0, 0, 0))
    ImageDraw.Draw(white_circle).ellipse(
        (0, 0, white_size - 1, white_size - 1), fill=LIGHT_COLOR)
    white_pos = ((size - white_size) // 2, (size - white_size) // 2)
    logo_pos = ((size - logo_size) // 2, (size - logo_size) // 2)
    img.alpha_composite(white_circle, white_pos)
    img.alpha_composite(logo_circle, logo_pos)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(out_path, "PNG", optimize=True)
    print(f"[png] wrote {out_path} ({size}x{size})")


def make_svg(url: str, size: int, logo_path: Path, out_path: Path) -> None:
    """Render a scalable SVG with an embedded base64 PNG logo."""
    qr = build_qr_matrix(url)
    img = qr.make_image(fill_color=DARK_COLOR, back_color=LIGHT_COLOR,
                        image_factory=SvgPathImage)
    buffer = io.BytesIO()
    img.save(buffer)
    svg = buffer.getvalue().decode("utf-8")

    # SvgPathImage uses QR-module coordinates regardless of its nominal mm size.
    viewbox_size = len(qr.get_matrix())
    svg = svg.replace(
        f'width="{viewbox_size}mm" height="{viewbox_size}mm"',
        f'width="{size}" height="{size}"',
        1,
    ).replace('fill="#000000"', f'fill="{DARK_COLOR}"', 1)
    svg_start = svg.index(">", svg.index("<svg")) + 1
    background = (f'<rect width="{viewbox_size}" height="{viewbox_size}" '
                  f'fill="{LIGHT_COLOR}"/>')
    svg = svg[:svg_start] + background + svg[svg_start:]

    logo_pil = Image.open(logo_path).convert("RGBA")
    logo_pil = crop_to_square(logo_pil)
    buf = io.BytesIO()
    logo_pil.save(buf, format="PNG")
    logo_b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    logo_size = viewbox_size * LOGO_RATIO
    center = viewbox_size / 2
    logo_pos = center - logo_size / 2
    white_radius = logo_size / 2 + WHITE_PAD_PX * viewbox_size / size
    inject = (
        f'<defs><clipPath id="logo-clip"><circle cx="{center:.3f}" '
        f'cy="{center:.3f}" r="{logo_size / 2:.3f}"/></clipPath></defs>'
        f'<circle cx="{center:.3f}" cy="{center:.3f}" r="{white_radius:.3f}" '
        f'fill="{LIGHT_COLOR}"/>'
        f'<image href="data:image/png;base64,{logo_b64}" x="{logo_pos:.3f}" '
        f'y="{logo_pos:.3f}" width="{logo_size:.3f}" height="{logo_size:.3f}" '
        f'preserveAspectRatio="xMidYMid slice" clip-path="url(#logo-clip)"/>'
    )
    svg = svg.replace("</svg>", f"{inject}</svg>", 1)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(svg, encoding="utf-8")
    print(f"[svg] wrote {out_path} ({len(svg.encode('utf-8'))} bytes)")


def main() -> int:
    """Parse command-line options and generate both QR assets."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=DEFAULT_URL, help="URL to encode")
    parser.add_argument("--out-dir", default=str(DEFAULT_OUT_DIR))
    parser.add_argument("--size", type=int, default=DEFAULT_SIZE)
    parser.add_argument("--logo", default=str(DEFAULT_LOGO))
    args = parser.parse_args()

    logo_path = Path(args.logo)
    if not logo_path.exists():
        print(f"error: logo not found at {logo_path}", file=sys.stderr)
        return 1
    if args.size <= 0:
        print("error: size must be greater than zero", file=sys.stderr)
        return 1

    out_dir = Path(args.out_dir)
    make_png(args.url, args.size, logo_path, out_dir / "ice-spirit-qr.png")
    make_svg(args.url, args.size, logo_path, out_dir / "ice-spirit-qr.svg")
    print(f"\nScan URL: {args.url}")
    return 0


if __name__ == "__main__":
    sys.exit(main())