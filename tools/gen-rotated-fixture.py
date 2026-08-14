#!/usr/bin/env python3
"""
Generate test_rotated.jpg: a JPEG with EXIF orientation=8 (rotate 90° CCW),
containing rotated visual text equivalent to app/src/androidTest/assets/test.png.

Run from project root:
    pip3 install Pillow piexif
    python3 tools/gen-rotated-fixture.py
"""
import sys
from PIL import Image

SRC = "app/src/androidTest/assets/test.png"
DST = "app/src/androidTest/assets/test_rotated.jpg"

# 1. Read PNG
img = Image.open(SRC).convert("RGB")
# 2. Rotate 90° CW (visual rotation)
rotated = img.transpose(Image.ROTATE_270)
# 3. Save as JPEG
rotated.save(DST, "JPEG", quality=92)

# 4. Set EXIF orientation tag = 8 (rotate 90° CCW to view upright).
# Visual rotation is 90° CW; EXIF says "rotate 90° CCW to upright",
# so BitmapLoader applies 270° CW (= 90° CCW) and the composition
# is 90° CW + 270° CW = 360° = upright.
try:
    import piexif
except ImportError:
    print("ERROR: piexif is required to generate the EXIF-rotated fixture.", file=sys.stderr)
    print("Install with: pip3 install piexif", file=sys.stderr)
    sys.exit(1)

exif_dict = {"0th": {piexif.ImageIFD.Orientation: 8}}
exif_bytes = piexif.dump(exif_dict)
piexif.insert(exif_bytes, DST)
print(f"Wrote {DST} with EXIF orientation=8")