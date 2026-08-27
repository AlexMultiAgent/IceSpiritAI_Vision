---
name: project-commit
description: Commits staged changes with strict IceSpiritAI_Vision hygiene — AlexMultiAgent author only, no Claude trailer, sensitive-file pre-flight, explicit git add paths. User-only — invoke explicitly via /project-commit before any commit.
disable-model-invocation: true
---

# Project Commit (IceSpiritAI_Vision)

Use when the user says "commit" / "提交" / "commit these changes" / "/project-commit".

## Hard rules (CLAUDE.md + memory)

1. Commit author MUST be `AlexMultiAgent` (verified via `git config user.name`).
2. NEVER append `Co-Authored-By: Claude ...` trailer.
3. NEVER use `git add -A` or `git add .` — explicit file paths only.
   The PreToolUse hook in `.claude/hooks/pre-tool-use.js` blocks these
   commands at the bash layer, but verify after staging too.
4. Sensitive files MUST NOT enter the index: `gradle.token.properties`,
   `local.properties`, `~/.gradle/gradle.properties`.
5. Commit author identity is locked by the repo's `git config user.name` —
   do not update.

## Pre-flight checklist (run in order, in parallel where possible)

### 1. JDK 17 toolchain (for any subsequent build)

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
```

### 2. Git author check

```bash
git config user.name   # MUST print "AlexMultiAgent"
git config user.email  # any — not enforced
```

If author ≠ `AlexMultiAgent`, abort:
"git config user.name is locked to AlexMultiAgent per CLAUDE.md; cannot override."

### 3. Working tree status (sensitive file scan)

```bash
git status --porcelain
```

If any line matches:

```
(A|M|\?\?)\s+.*(gradle\.token\.properties|local\.properties|\.gradle/gradle\.properties)
```

abort:
"Sensitive file detected in working tree. See .gitignore. Do NOT stage this."

### 4. Build verification (when changed files include .kt / .gradle.kts / assets/rules/*.json / assets/*.md)

```bash
./gradlew.bat testDebugUnitTest -PmodelProfile=shell
```

Must end with `BUILD SUCCESSFUL`. If FAIL, fix root cause first.

### 5. Stage explicitly

```bash
git add <path1> <path2> ...
```

NEVER `git add -A`. NEVER `git add .`. Use the exact paths the user asked
to commit, or the paths surfaced by `git status --porcelain`.

### 6. Verify staged set

```bash
git diff --cached --name-only
```

Re-check no sensitive file is staged.

### 7. Commit via HEREDOC

```bash
git commit -m "$(cat <<'EOF'
<one-line subject>

[optional body — what / why, kept short]

EOF
)"
```

Do NOT pass `-C`, `-c`, `--amend`, `--no-verify`, `--no-gpg-sign`, etc.

### 8. Verify commit

```bash
git log -1 --format='%h %an %s%n%b'
```

Assert:
- `%an` == `AlexMultiAgent`
- The body does NOT contain `Co-Authored-By: Claude` (case-insensitive)

If either assertion fails, the commit did not satisfy IceSpiritAI_Vision
hygiene — abort and report.

## Commit message style

Follow recent history:

```
feat(rules): <one-line subject>
chore(build): <one-line subject>
docs(kb): <one-line subject>
fix(ocr): <one-line subject>
refactor(ui): <one-line subject>
```

Scope tags in parens: `rules` / `build` / `kb` / `ui` / `ocr` / `updater` /
`export` / `infra` / `meta`.

Subject line ≤ 72 chars. Body wraps at 72.

## Release 三段式打标 (tag + version + changelog)

When the commit subject includes a release marker (`feat(v0.1.X):` /
`fix(v0.1.X):` / explicit "发版" / "release"), this skill performs the
**three-piece tag** synchronously, all referencing the same commit SHA:

1. **`versionCode` bump** in `app/build.gradle.kts`
   - Find the current `versionCode N` and bump by 1
   - Find the current `versionName "X.Y.Z"` and bump per CLAUDE.md
     (per release hygiene: only actual feature/fix changes bump version)

2. **`user-changelog.md` top entry** — prepend a v0.1.X section:

   ```markdown
   ## v0.1.X — <YYYY-MM-DD>

   - <commit subject 1>
   - <commit subject 2>
   - ...

   ### 修复
   - <fix list>

   ### 变更
   - <change list>
   ```

   Verify first paragraph parses via:
   ```bash
   python3 -c "
   import sys, re
   md = open('app/src/main/assets/user-changelog.md').read()
   first = md.split('\n## ', 1)[1].split('\n', 1)[0]
   assert first.startswith('v0.1.X'), f'stale first entry: {first!r}'
   print('first entry:', first)
   "
   ```

3. **`git tag v0.1.X` + push `latest` ref**:
   ```bash
   git tag v0.1.X
   git push origin v0.1.X
   git tag -f latest
   git push origin :latest
   git push origin latest
   ```

**Why all three must move together**: `app/build.gradle.kts uploadVisionReleaseToGitea` reads `versionCode` to generate `vision-latest.json.versionCode`; `user-changelog.md` is rendered into the in-app update dialog; `git tag v0.1.X` is what Gitea's `releases/download/latest/<file>` routes resolve against. Splitting introduces the v0.1.14-style drift where the APK is live but JSON shows the old version.

**Pre-flight for tag push**:
- The release APK must already be verified live (per `icevision-release` post-release smoke)
- The commit must already be authored by `AlexMultiAgent`
- Local HEAD must equal the release SHA — `git rev-parse HEAD` should match the APK's expected commit

## Returns

A short summary:
- Commit hash (short)
- Author verified (AlexMultiAgent)
- Files staged + committed (count)
- BUILD SUCCESSFUL status (if verification was run)

If any pre-flight check fails, return the failure reason and DO NOT commit.