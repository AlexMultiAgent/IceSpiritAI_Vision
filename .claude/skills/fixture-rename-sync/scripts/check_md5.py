#!/usr/bin/env python3
"""Verify all 66 fixture ↔ 违规案例 image md5 hashes match.

Exits non-zero on count mismatch, missing file, or md5 mismatch.

Run from repo root:
    python .claude/skills/fixture-rename-sync/scripts/check_md5.py
"""
import sys
from pathlib import Path

# Force UTF-8 stdout (Windows cp936 garbles Chinese / bullets)
try:
    sys.stdout.reconfigure(encoding="utf-8")
except (AttributeError, OSError):
    pass

REPO = Path(__file__).resolve().parents[4]
FIXTURE_DIR = REPO / "app/src/androidTest/assets/fixtures/audit66"
VIOLATION_DIR = REPO / "违规案例"

EXTS = {".png", ".jpg", ".jpeg"}


def md5(path: Path) -> str:
    import hashlib
    h = hashlib.md5()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    fixture_files = sorted(p for p in FIXTURE_DIR.iterdir() if p.suffix.lower() in EXTS)
    violation_files = sorted(p for p in VIOLATION_DIR.iterdir() if p.suffix.lower() in EXTS)

    print(f"fixture ({FIXTURE_DIR.relative_to(REPO)}): {len(fixture_files)} files")
    print(f"违规案例 ({VIOLATION_DIR.relative_to(REPO)}): {len(violation_files)} files")

    fixture_by_name = {p.name: p for p in fixture_files}
    violation_by_name = {p.name: p for p in violation_files}

    only_in_fixture = sorted(set(fixture_by_name) - set(violation_by_name))
    only_in_violation = sorted(set(violation_by_name) - set(fixture_by_name))
    common = sorted(set(fixture_by_name) & set(violation_by_name))

    mismatches: list[str] = []

    if only_in_fixture:
        print(f"\nOnly in fixture ({len(only_in_fixture)}):")
        for n in only_in_fixture:
            print(f"  {n}")
    if only_in_violation:
        print(f"\nOnly in 违规案例 ({len(only_in_violation)}):")
        for n in only_in_violation:
            print(f"  {n}")

    for name in common:
        if md5(fixture_by_name[name]) != md5(violation_by_name[name]):
            mismatches.append(name)

    if mismatches:
        print(f"\nmd5 mismatches ({len(mismatches)}):")
        for n in mismatches:
            print(f"  {n}")
        return 2

    if only_in_fixture or only_in_violation:
        return 2

    print(f"\nAll {len(common)} md5 hashes match. PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())