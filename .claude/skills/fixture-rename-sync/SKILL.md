---
name: fixture-rename-sync
description: Syncs renamed fixture images across 6 markdown docs (coverage_matrix.md / audit_gaps.md / _违规档案总册.md / _coverage_matrix.md / _audit_gaps.md / _rename_map.md), copies images to keep fixture ↔ 违规案例 md5 in sync, and flags stale section content in _违规档案总册.md that may still describe the OLD image. Use after manually renaming fixture files in app/src/androidTest/assets/fixtures/audit66/ or 违规案例/, or when the user says "/fixture-rename-sync" / "图片重命名了同步文档" / "fixture rename sync". User-only — invoke explicitly because it modifies 6+ files.
disable-model-invocation: true
---

# Fixture Rename Sync

When fixture images in `app/src/androidTest/assets/fixtures/audit66/` or
`违规案例/` are renamed (manually or via batch script), six markdown docs
reference the filenames by literal string and must update in lockstep.

This skill detects renames, applies bare-filename swaps, copies images to
keep fixture ↔ 违规案例 md5 in sync, appends a session entry to
`_rename_map.md`, and flags `_违规档案总册.md` sections that may still
describe the OLD image semantic (so you know to rewrite them).

## What gets synced

| File | Direction |
|---|---|
| `app/src/androidTest/assets/fixtures/audit66/coverage_matrix.md` | in-place rewrite |
| `app/src/androidTest/assets/fixtures/audit66/audit_gaps.md` | in-place rewrite |
| `违规案例/_违规档案总册.md` | in-place rewrite (filenames only; section content stays) |
| `违规案例/_coverage_matrix.md` | sync from fixture (`shutil.copy2`) |
| `违规案例/_audit_gaps.md` | sync from fixture (`shutil.copy2`) |
| `违规案例/_rename_map.md` | append session header + bullets (idempotent per date) |
| image files (fixture → 违规案例) | copy if md5 mismatch |

## Workflow

### Phase 1 — Detect renames (or supply manual pairs)

Two modes:

**Auto-detect from git** (default; works when renames are committed or
staged via `git mv` / `git add -u`):

```bash
git diff --find-renames=50% --name-status HEAD -- \
  app/src/androidTest/assets/fixtures/audit66/ 违规案例/
```

Output lines starting with `R` give `R<score>\t<old_relpath>\t<new_relpath>`.
The script filters to image extensions (.png/.jpg/.jpeg).

**Manual pairs** (when renames are filesystem-only, no git history):

```bash
python .claude/skills/fixture-rename-sync/scripts/rename_sync.py \
  --pairs "old1.png,new1.png;old2.jpg,new2.jpg"
# Comma or arrow separator; semicolon between pairs
# --pairs "old1→new1;old2→new2"
```

### Phase 2 — Run sync

```bash
python .claude/skills/fixture-rename-sync/scripts/rename_sync.py
# or with manual pairs:
python .claude/skills/fixture-rename-sync/scripts/rename_sync.py \
  --pairs "old1.png,new1.png;old2.jpg,new2.jpg"
# add --dry-run to detect + report without modifying files
```

The script:

1. Builds (old_filename, new_filename) pairs from git or `--pairs` arg.
2. Applies bare-filename swap to 3 in-place .md docs (counts swaps/file).
3. Copies fixture → 违规案例 for each renamed image (md5 verify; only
   copies on mismatch or missing target).
4. Verifies zero-leak: greps every old filename across all 6 docs +
   rename map; exits non-zero (code 2) on any leak.
5. Detects stale sections in `_违规档案总册.md`: for each renamed slot,
   extracts top-3 keywords (length ≥ 4) from the new filename's tail;
   if the slot's `## NN — ...` section body contains NONE of those
   keywords, the section likely describes the OLD image and needs
   manual rewrite.
6. Appends a dated session header + bullets to `_rename_map.md`
   (idempotent — won't duplicate same-day entries with same bullets).
7. Syncs fixture → 违规案例 .md docs via `shutil.copy2`.

### Phase 3 — Verify image md5 + manual rewrite

```bash
python .claude/skills/fixture-rename-sync/scripts/check_md5.py
```

Should print `All 66 md5 hashes match. PASS`. Non-zero exit (code 2) =
investigate. Outputs `only-in fixture` / `only-in 违规案例` lists on count
mismatch so you can diagnose drift.

For each stale section flagged in Phase 2:

1. Open the new fixture image (e.g. via the IDE image viewer) to see
   the actual content.
2. Compare against `_违规档案总册.md`'s `## NN — ...` section — if the
   section still describes the OLD image's 广告主体 / 原始违法广告语 /
   违规情形, rewrite it.
3. Use `coverage_matrix.md` §2 (the row with the slot's bucket /
   rules / status) as the source of truth for the new content's
   compliance profile.

## What the script does NOT do

- **Does not auto-rewrite section content** — `_违规档案总册.md` section
  content requires human judgment about the new image's actual semantic.
  The script flags stale sections; you rewrite.
- **Does not commit** — commit is the `project-commit` skill's job.
- **Does not stage** — `git add` is yours to run (per the PreToolUse
  hook, `git add -A` / `git add .` is blocked; use specific paths or
  `git add -u` for tracked-file modifications).

## Gotchas

- **`_coverage_matrix.md` / `_audit_gaps.md` are sync copies.** Editing
  them directly gets clobbered on next sync. Edit the fixture masters.
- **Encoding**: cp936 garbles Chinese in `python` stdout on Windows.
  The skill uses UTF-8 forced stdout (see `scripts/rename_sync.py` top)
  and binary I/O for file ops; redirect verification output via
  `> file.txt` then Read, or use `Out-File -Encoding utf8` from PowerShell.
- **LF vs CRLF**: scripts read/write in **binary mode** to preserve LF
  line endings (Windows text mode silently converts LF → CRLF, which
  would dirty every doc on every sync). Don't refactor to text mode.
- **66-image md5 invariant**: after every sync, run `check_md5.py`. If
  it fails, the fixture or 违规案例 dir drifted — restore from the
  other dir (`shutil.copy2 fixture_dir/*.png violation_dir/`).
- **`git diff --find-renames` threshold**: 50% similarity is default.
  If a rename drops below, pass `--find-renames=30%` to git, or use
  manual `--pairs`.
- **Filesystem renames outside git**: if the user does `mv old new` and
  neither old nor new is tracked, `git diff --find-renames` won't see
  them — pass `--pairs` explicitly. Tip: run `git add -u` first if the
  deletions were on tracked files (so git sees the rename).
- **Stale section heuristic**: keywords ≥ 4 chars to avoid false
  positives on short tokens like "教育" or "广告". If the new
  filename has no such keywords (all parts are short), the section is
  not flagged — that means the filename alone is ambiguous and you'll
  need to manually verify.
- **`_rename_map.md` session append**: idempotent on date, not on
  bullets. Re-running the same day with same pairs is a no-op.
  Re-running with new pairs appends under the same day header.

## Files in this skill

```
.claude/skills/fixture-rename-sync/
├── SKILL.md
└── scripts/
    ├── rename_sync.py   # main sync: detect → swap → copy → verify → append → flag
    └── check_md5.py     # 66-image md5 invariant verifier
```