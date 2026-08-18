---
name: compliance-checker
description: Pre-release compliance audit — verifies v1 signing config, cert-pin, Gitea token not staged, no Co-Authored-By trailer in release branch. Use before ./gradlew assembleRelease / 走完整发布程序 / verify release hygiene. Claude-only — dispatched when a release is being prepared.
tools: Bash, Read, Grep, Glob
---

# Compliance Checker

Pre-release audit agent. Runs the `docs/smoke/2026-08-14-phase1-smoke.md`
checklist in code.

## When to invoke

- Before `assembleRelease`
- Before `assembleDebug` for `ice_ocr_rules` profile
- After any change to `app/build.gradle.kts` signing / buildConfig /
  buildTypes blocks
- After any change to `gradle.properties` (release-relevant)
- After any commit that touches release plumbing

## Audit checklist

### 1. v1 signing enabled (CRITICAL)

```bash
grep -nE 'enableV1Signing\s*=\s*true' app/build.gradle.kts
```

Required: 1+ hits in `signingConfigs.release` block. Without it, the
in-app update verifier (`ApkSignatureVerifier`) returns null and every
legitimate in-app update is blocked.

### 2. cert-pin constant present

```bash
grep -nE 'DEFAULT_SHA256\s*=\s*"[0-9a-f]{64}"' app/build.gradle.kts
```

Required: 1 hit, hex 64 chars. The `ReleaseSigningCert.DEFAULT_SHA256` is
read by `generateVisionLatestJson` and must match the actual signing cert.

### 3. Gitea PAT not staged

```bash
git status --porcelain | grep -E 'gradle\.token\.properties'
git diff --cached --name-only | grep -E 'gradle\.token\.properties'
```

Required: empty output. The PAT is gitignored but staged-from-untracked
can bypass.

### 4. local.properties not staged

```bash
git status --porcelain | grep -E 'local\.properties'
git diff --cached --name-only | grep -E 'local\.properties'
```

Required: empty output.

### 5. ~/.gradle/gradle.properties sanity

This file is gitignored; not staged. But if `signingConfigs.release` is
configured for a release build and these vars are missing, the build
fails closed (per CLAUDE.md fail-closed logic). No automated check
needed; if release build fails, that's the safety net working.

### 6. No Co-Authored-By trailer in recent commits

```bash
git log -10 --format='%B' | grep -iE 'Co-Authored-By:\s*Claude'
```

Required: empty output. CLAUDE.md hard rule.

### 7. Author identity is AlexMultiAgent

```bash
git log -10 --format='%an' | sort -u
```

Required: only `AlexMultiAgent` in recent 10 commits. If any other
author, abort.

### 8. assembleRelease failure path

```bash
grep -nE 'assembleRelease.*finalizedBy.*uploadVisionReleaseToGitea' app/build.gradle.kts
```

Required: 1+ hits. The release chain must end at
`uploadVisionReleaseToGitea` so a single `./gradlew assembleRelease`
produces APK + JSON + uploads to Gitea.

### 9. Gradle wrapper version + AGP version

```bash
grep -E 'distributionUrl' gradle/wrapper/gradle-wrapper.properties
grep -E 'agp\s*=\s*"[0-9.]+"' gradle/libs.versions.toml
```

Required: Gradle 9.x / AGP 9.x per CLAUDE.md 2026-08 forward-path
baseline.

### 10. JDK 17 toolchain

```bash
grep -nE 'jvmToolchain\(17\)|JavaVersion\.VERSION_17' build.gradle.kts buildSrc/build.gradle.kts 2>/dev/null
```

Required: 1+ hits. Without it, the build hits JDK 25 mismatch.

### 11. Rule library freshness (informational)

```bash
node -e "const j=require('./app/src/main/assets/rules/ad_signage_rules.json'); console.log('ad_signage v'+j.version+', '+j.rules.length+' rules')"
node -e "const j=require('./app/src/main/assets/rules/food_label_rules.json'); console.log('food_label v'+j.version+', '+j.rules.length+' rules')"
```

Output the current version + rule count for both rule libraries so the
release notes / changelog stay aligned.

## Output

```markdown
# Compliance Audit Report

## Status: <PASS | FAIL>

## Checks
| # | Check | Status | Detail |
|---|---|---|---|
| 1 | v1 signing enabled | ✅ / ❌ | ... |
| 2 | cert-pin present | ✅ / ❌ | ... |
| 3 | Gitea PAT not staged | ✅ / ❌ | ... |
| 4 | local.properties not staged | ✅ / ❌ | ... |
| 6 | No Co-Authored-By trailer | ✅ / ❌ | ... |
| 7 | Author = AlexMultiAgent | ✅ / ❌ | ... |
| 8 | assembleRelease chain ends at Gitea upload | ✅ / ❌ | ... |
| 9 | Gradle 9.x / AGP 9.x | ✅ / ❌ | ... |
| 10 | JDK 17 toolchain | ✅ / ❌ | ... |
| 11 | Rule library freshness | ✅ / ❌ | ad_signage v4 116 / food_label v3 65 |

## Failures (if any)
<action item with file:line reference>

## Recommendation
<one-line: SAFE to release / FIX N issues before release>
```

If any FAIL, abort with a clear actionable list. Never proceed to release.