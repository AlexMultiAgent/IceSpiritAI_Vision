#!/usr/bin/env python3
"""Fixture rename sync.

Applies bare-filename swaps across 6 markdown docs after fixture renames,
syncs image files (md5 verify + copy), appends session entries to
_rename_map.md, and detects stale section content in _违规档案总册.md that
may describe the OLD image semantic.

Auto-detects renames via `git diff --find-renames`, or accepts manual via
--pairs "old1,new1;old2,new2".

Run from repo root:
    python .claude/skills/fixture-rename-sync/scripts/rename_sync.py
    python .claude/skills/fixture-rename-sync/scripts/rename_sync.py \\
        --pairs "old1.png,new1.png;old2.jpg,new2.jpg"
"""
import argparse
import datetime
import hashlib
import io
import re
import shutil
import subprocess
import sys
from pathlib import Path

# Force UTF-8 stdout (Windows cp936 garbles Chinese / bullets)
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "buffer"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# .claude/skills/<name>/scripts/foo.py → parents[4] = repo root
REPO = Path(__file__).resolve().parents[4]
FIXTURE_DIR = REPO / "app/src/androidTest/assets/fixtures/audit66"
VIOLATION_DIR = REPO / "违规案例"

DOCS_MASTER_INPLACE = [
    FIXTURE_DIR / "coverage_matrix.md",
    FIXTURE_DIR / "audit_gaps.md",
    VIOLATION_DIR / "_违规档案总册.md",
]
DOCS_SYNC_FROM_FIXTURE = [
    (FIXTURE_DIR / "coverage_matrix.md", VIOLATION_DIR / "_coverage_matrix.md"),
    (FIXTURE_DIR / "audit_gaps.md", VIOLATION_DIR / "_audit_gaps.md"),
]
RENAME_MAP = VIOLATION_DIR / "_rename_map.md"

IMAGE_EXTS = {".png", ".jpg", ".jpeg"}


def md5(path: Path) -> str:
    h = hashlib.md5()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_pairs_arg(arg: str) -> list[tuple[str, str]]:
    pairs: list[tuple[str, str]] = []
    for entry in arg.split(";"):
        entry = entry.strip()
        if not entry:
            continue
        if "," in entry:
            old, new = entry.split(",", 1)
        elif "→" in entry:
            old, new = entry.split("→", 1)
        else:
            print(f"WARN: skip malformed pair entry: {entry!r}", file=sys.stderr)
            continue
        pairs.append((old.strip(), new.strip()))
    return pairs


def detect_pairs_from_git() -> list[tuple[str, str]]:
    """git diff --find-renames against HEAD; image files only."""
    if not (REPO / ".git").exists():
        return []
    try:
        proc = subprocess.run(
            ["git", "diff", "--find-renames=50%", "--name-status", "HEAD", "--",
             str(FIXTURE_DIR.relative_to(REPO)) + "/",
             str(VIOLATION_DIR.relative_to(REPO)) + "/"],
            cwd=REPO, capture_output=True, text=True, encoding="utf-8",
            check=False,
        )
    except FileNotFoundError:
        print("ERR: git not on PATH", file=sys.stderr)
        return []
    pairs: list[tuple[str, str]] = []
    for line in proc.stdout.splitlines():
        if not line.startswith("R"):
            continue
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        old_name = Path(parts[1]).name
        new_name = Path(parts[2]).name
        if old_name.split(".")[-1].lower() not in IMAGE_EXTS:
            continue
        pairs.append((old_name, new_name))
    return pairs


def apply_swaps(pairs: list[tuple[str, str]], targets: list[Path]) -> dict[Path, int]:
    """Apply bare-filename swaps. Read/write in binary mode to preserve LF line
    endings (Windows text mode converts LF to CRLF)."""
    counts: dict[Path, int] = {}
    for path in targets:
        if not path.exists():
            counts[path] = 0
            continue
        raw = path.read_bytes()
        try:
            content = raw.decode("utf-8")
        except UnicodeDecodeError:
            counts[path] = 0
            continue
        n = 0
        for old, new in pairs:
            cnt = content.count(old)
            if cnt:
                content = content.replace(old, new)
                n += cnt
        if n:
            path.write_bytes(content.encode("utf-8"))
        counts[path] = n
    return counts


def sync_images(pairs: list[tuple[str, str]]) -> list[str]:
    actions: list[str] = []
    for _old, new in pairs:
        src = FIXTURE_DIR / new
        dst = VIOLATION_DIR / new
        if not src.exists():
            actions.append(f"MISSING-FIXTURE: {new}")
            continue
        if dst.exists() and md5(src) == md5(dst):
            actions.append(f"OK-MATCH: {new}")
            continue
        if dst.exists():
            actions.append(f"MD5-MISMATCH-OVERWRITE: {new}")
        else:
            actions.append(f"COPIED-NEW: {new}")
        shutil.copy2(src, dst)
    return actions


def verify_zero_leak(pairs: list[tuple[str, str]], targets: list[Path]) -> dict[Path, list[str]]:
    leaks: dict[Path, list[str]] = {}
    for old, _ in pairs:
        for path in targets:
            if not path.exists():
                continue
            try:
                content = path.read_bytes().decode("utf-8")
            except UnicodeDecodeError:
                continue
            if old in content:
                leaks.setdefault(path, []).append(old)
    return leaks


def detect_stale_sections(pairs: list[tuple[str, str]]) -> list[tuple[int, list[str], str]]:
    """For each renamed slot, extract top-3 keywords from new filename tail;
    flag if NONE appear in the slot's `## NN — ...` section body."""
    master = VIOLATION_DIR / "_违规档案总册.md"
    if not master.exists():
        return []
    content = master.read_bytes().decode("utf-8")

    slot_to_new: dict[int, str] = {}
    for _old, new in pairs:
        m = re.match(r'(\d+)_', new)
        if m:
            slot_to_new[int(m.group(1))] = new

    stale: list[tuple[int, list[str], str]] = []
    for slot, new_name in slot_to_new.items():
        tail_m = re.match(r'\d+_(.+?)\.[a-z]+$', new_name, re.I)
        if not tail_m:
            continue
        new_tail = tail_m.group(1)
        # Top-3 keywords with len >= 4 to avoid short-token false positives
        new_keywords = [kw for kw in new_tail.split('_') if len(kw) >= 4][:3]
        if not new_keywords:
            continue
        sec_re = re.compile(rf'^## {slot:02d} — (.+?)(?=\n## |\Z)', re.S | re.M)
        sec_match = sec_re.search(content)
        if not sec_match:
            continue
        sec_body = sec_match.group(0)
        hits = sum(1 for kw in new_keywords if kw in sec_body)
        if hits == 0:
            preview = sec_body[:120].replace('\n', ' ')
            stale.append((slot, new_keywords, preview))
    return stale


def append_rename_map(pairs: list[tuple[str, str]]) -> int:
    """Append session header + bullets to _rename_map.md (idempotent on date).
    Binary I/O to preserve LF line endings."""
    if not RENAME_MAP.exists():
        return 0
    content = RENAME_MAP.read_bytes().decode("utf-8")
    today = datetime.date.today().isoformat()
    sec_header = f"\n\n## {today} 会话增量\n\n"
    if sec_header not in content:
        body = content + sec_header
    else:
        idx = content.index(sec_header) + len(sec_header)
        body = content[:idx]

    bullets = [f"- `{old}` → `{new}`" for old, new in pairs]
    new_bullets = [b for b in bullets if b not in body]
    if not new_bullets:
        return 0
    RENAME_MAP.write_bytes((body + "\n".join(new_bullets) + "\n").encode("utf-8"))
    return len(new_bullets)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pairs", help='Manual pairs "old,new;old,new" (use → or ,)')
    ap.add_argument("--dry-run", action="store_true",
                    help="Detect + report only; do not modify any file")
    args = ap.parse_args()

    pairs: list[tuple[str, str]] = []
    if args.pairs:
        pairs = parse_pairs_arg(args.pairs)
    else:
        pairs = detect_pairs_from_git()

    if not pairs:
        print("No renames detected.")
        print("  - For git-tracked/staged renames, ensure `git diff --find-renames` shows R entries.")
        print("  - For filesystem renames, pass --pairs 'old,new;old,new' explicitly.")
        return 0

    print(f"=== fixture-rename-sync ===")
    print(f"Detected {len(pairs)} rename(s):")
    for old, new in pairs:
        print(f"  {old} → {new}")

    if args.dry_run:
        print("\n(--dry-run: no file changes will be made)")
        return 0

    # 1. Apply swaps to in-place docs (fixture masters + 总册)
    in_place_docs: list[Path] = list(DOCS_MASTER_INPLACE)
    for src, _ in DOCS_SYNC_FROM_FIXTURE:
        in_place_docs.append(src)
    counts = apply_swaps(pairs, in_place_docs)

    # 2. Sync fixture → 违规案例 image files (md5 verify + copy)
    actions = sync_images(pairs)

    # 3. Sync fixture → 违规案例 .md docs (coverage_matrix, audit_gaps)
    #    Done BEFORE leak check so the sync copies are up-to-date when checked.
    for src, dst in DOCS_SYNC_FROM_FIXTURE:
        shutil.copy2(src, dst)

    # 4. Verify zero leak: only the docs the script is responsible for syncing.
    #    _rename_map.md is EXCLUDED — it is a history file that legitimately
    #    contains old filenames (mapped from raw timestamp slugs).
    leak_targets: list[Path] = list(in_place_docs)
    for _, dst in DOCS_SYNC_FROM_FIXTURE:
        leak_targets.append(dst)
    leaks = verify_zero_leak(pairs, leak_targets)

    # 5. Detect stale sections in _违规档案总册.md
    stale = detect_stale_sections(pairs)

    # 6. Append session entry to _rename_map.md (idempotent)
    appended = append_rename_map(pairs)

    # Report
    print(f"\nSwaps per file:")
    for path, n in counts.items():
        rel = path.relative_to(REPO)
        if n:
            print(f"  {rel}: {n}")

    print(f"\nImage sync: {len(actions)} actions")
    for a in actions:
        print(f"  {a}")

    if leaks:
        leak_count = sum(len(v) for v in leaks.values())
        print(f"\nLEAK DETECTED — {leak_count} stale reference(s):")
        for path, olds in leaks.items():
            print(f"  {path.relative_to(REPO)}: {olds}")
        return 2
    print(f"\nZero-leak verification: PASS")

    if stale:
        print(f"\nStale sections: {len(stale)} (manual rewrite needed in _违规档案总册.md)")
        for slot, kws, preview in stale:
            print(f"  #{slot:02d}: missing keywords {kws}")
            print(f"         preview: {preview!r}")
    else:
        print(f"\nStale sections: 0 (clean)")

    print(f"\n_rename_map.md appended: {appended} entries")
    return 0


if __name__ == "__main__":
    sys.exit(main())