#!/usr/bin/env python3
"""
Generate test_rotated.jpg: a JPEG with EXIF orientation=6 (rotate 90° CW),
containing rotated visual text equivalent to app/src/androidTest/assets/test.png.

Run from project root:
    pip3 install Pillow piexif
    python3 tools/gen-rotated-fixture.py
"""
from PIL import Image

SRC = "app/src/androidTest/assets/test.png"
DST = "app/src/androidTest/assets/test_rotated.jpg"

# 1. Read PNG
img = Image.open(SRC).convert("RGB")
# 2. Rotate 90° CW
rotated = img.transpose(Image.ROTATE_270)
# 3. Save as JPEG
rotated.save(DST, "JPEG", quality=92)

# 4. Set EXIF orientation tag = 6 (rotated 90° CW)
try:
    import piexif
    exif_dict = {"0th": {piexif.ImageIFD.Orientation: 6}}
    exif_bytes = piexif.dump(exif_dict)
    piexif.insert(exif_bytes, DST)
    print(f"Wrote {DST} with EXIF orientation=6")
except ImportError:
    print(f"Wrote {DST} WITHOUT EXIF orientation tag (piexif not installed)")
    print("Install with: pip3 install piexif")