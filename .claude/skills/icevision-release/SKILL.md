---
name: icevision-release
description: Walks IceSpiritAI_Vision release pipeline (assembleRelease → generateVisionLatestJson → archiveVisionRelease → uploadVisionReleaseToGitea) with cert-pin pre-flight, Gitea route availability check, and recovery shortcuts for the documented footguns (Gitea 1.22.x APK 404, large-file POST timeout, v1 signing must be enabled). Use when user says /icevision-release or "发版" / "release" / "走发布流水线". User-only — invoke explicitly before release.
disable-model-invocation: true
---

# IceSpiritAI_Vision Release Pipeline

Walks the 4-step Gitea release flow with pre-flight checks that catch the
documented footguns in `CLAUDE.md` and `docs/smoke/2026-08-14-phase1-smoke.md`.

## Pre-flight (always run first, in parallel)

### 1. JDK 17 toolchain

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
```

### 2. v1 signing must be enabled

```bash
grep -n "enableV1Signing" app/build.gradle.kts
# Must print `enableV1Signing = true` (or `true` in any line under signingConfigs.release).
# AGP defaults to v2-only; in-app update verifier uses JarFile + META-INF/CERT.RSA
# for cert-pin and returns null if APK is v2/v3 only.
```

### 3. Gitea PAT present (but not in repo)

```bash
test -f gradle.token.properties && echo "PAT present" || echo "MISSING — copy from gradle.token.properties.example"
# .gitignore MUST keep gradle.token.properties out of the repo.
```

### 4. AAR + ONNX models present

```bash
ls -lh app/libs/ppocr-sdk.aar app/src/main/assets/models/det/inference.onnx app/src/main/assets/models/rec/inference.onnx 2>/dev/null
# If any missing: run tools/download-ppocr-models.sh + tools/build-ppocr-sdk.sh
```

### 5. Cert-pin gate pre-flight (matches release config)

```bash
# SHA-256 of the release cert must match what the in-app verifier expects.
# For AlexMultiAgent's setup, signerCertSha256 should equal 4a21f4...3043.
keytool -list -v -keystore ~/.gradle/release.jks -alias icespiritai -storepass "$KEYSTORE_PASS" 2>/dev/null | grep "SHA256:"
# If SHA256 differs, update the verifier in app/src/main/java/.../updater/ BEFORE release.
```

## The 4-step pipeline

| Step | Gradle task | Purpose | Known footgun |
|---|---|---|---|
| 1 | `assembleRelease` | Build signed APK | R8 + Lint + native may OOM the daemon. Use ≥8 GiB. |
| 2 | `generateVisionLatestJson` | Write `app/build/outputs/apk/release/vision-latest.json` with `apkUrl` / `versionCode` / `signerCertSha256` | URL is hardcoded to `releases/download/latest/icespiritai-vision.apk` — **broken route on Gitea 1.22.x** |
| 3 | `archiveVisionRelease` | Stage to `build/generated/release-staging/` (per memory: never write to `发布版历史存档/`) | — |
| 4 | `uploadVisionReleaseToGitea` | POST APK + JSON to Gitea, with cert-pin verify | Large-file POST sometimes returns HTTP 100 and stalls. Mitigation: POST JSON first (small, ~1s), then APK with `--max-time 900` |

## The Gitea 1.22.x APK 404 workaround (CRITICAL)

Per CLAUDE.md (v0.1.31 release footgun), `releases/download/latest/icespiritai-vision.apk`
returns HTTP 404 even though `releases/download/latest/vision-latest.json` returns 200.
The upload task already handles this by:

1. POSTing the APK to Gitea and extracting the `uuid` from the response:
   ```json
   { "id": 260, "uuid": "39c59ab3-..." }
   ```
2. Rewriting `apkUrl` in `vision-latest.json` to:
   ```
   http://125.211.45.14:3000/attachments/<uuid>
   ```
3. POSTing the rewritten JSON.

**Verify after upload**:
```bash
curl -sI http://125.211.45.14:3000/attachments/<uuid> | grep -E "HTTP|Content-Length"
# Must return HTTP 200 + Content-Length matching local APK size.
```

## The large-file POST timeout recovery

If `uploadVisionReleaseToGitea` hangs (HTTP 100, no final status):

- DO NOT roll back code. The cert-pin already passed locally.
- Manually POST in this order:
  1. `vision-latest.json` (1.4 KB, ~1 s, no timeout risk)
  2. `icespiritai-vision.apk` with `--max-time 900` (was 600 — too tight for 70 MB+ AAR-bundled APK)
  3. DELETE any duplicate asset ids (curl may leave half-uploaded records).

## Post-release smoke (must verify)

```bash
# 1. JSON metadata is reachable + has correct versionCode + cert SHA256
curl -s http://125.211.45.14:3000/releases/download/latest/vision-latest.json | python3 -c "
import sys, json
d = json.load(sys.stdin)
assert d['versionCode'] >= 14, f'stale versionCode: {d[\"versionCode\"]}'
assert d['signerCertSha256'].startswith('4a21f4'), f'signerCertSha256 mismatch: {d[\"signerCertSha256\"]}'
print('JSON OK:', d['versionCode'], d['signerCertSha256'])
"

# 2. APK is reachable + correct size
LOCAL_SIZE=$(stat -c %s app/build/outputs/apk/release/icespiritai-vision.apk)
REMOTE_SIZE=$(curl -sI http://125.211.45.14:3000/attachments/<uuid> | awk '/Content-Length/{print $2}' | tr -d '\r')
test "$LOCAL_SIZE" = "$REMOTE_SIZE" && echo "SIZE OK ($LOCAL_SIZE)" || echo "SIZE MISMATCH local=$LOCAL_SIZE remote=$REMOTE_SIZE"

# 3. (Optional) In-app update smoke on Huawei nova 6 — see docs/smoke/2026-08-14-phase1-smoke.md
```

## Things NEVER to do during release

- ❌ `git add -A` or `git add .` (PreToolUse hook blocks; CLAUDE.md forbids)
- ❌ Append `Co-Authored-By: Claude ...` trailer (CLAUDE.md forbids; commits are AlexMultiAgent only)
- ❌ Commit `gradle.token.properties` or `~/.gradle/gradle.properties` (Gitea PAT + release creds leak)
- ❌ Write to `发布版历史存档/` (per memory `feedback-no-release-history-archive.md` — staging now goes to `build/generated/release-staging/`)
- ❌ Update `git config` (never touch global git config)
- ❌ `--no-verify` / `--no-gpg-sign` (bypasses pre-commit hooks that enforce hygiene)

## After release: tag + changelog + version bump

This skill **does not** bump `versionCode`, write `user-changelog.md`, or push the
`latest` git tag. Those three move together and belong at **commit time** — see
`.claude/skills/project-commit/SKILL.md` "Release 三段式打标" section:

1. `versionCode` bump in `app/build.gradle.kts`
2. `user-changelog.md` entry at the top
3. `git tag v0.1.X` + push `latest` ref

Call `project-commit` AFTER all release artifacts are verified (post-release smoke
above), so the tag points at the exact SHA whose APK + JSON are live on Gitea.

## See also

- `CLAUDE.md` — Release pipeline footguns (Gitea 1.22.x APK 404, large-file POST, v1 signing)
- `docs/smoke/2026-08-14-phase1-smoke.md` — Phase 1 release smoke record
- `docs/smoke/2026-08-14-phase2-smoke.md` — Phase 2 hardening release smoke
- `.claude/skills/project-commit/SKILL.md` — Commit hygiene + release 三段式打标