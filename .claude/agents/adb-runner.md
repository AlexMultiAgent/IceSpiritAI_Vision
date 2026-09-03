---
name: adb-runner
description: Wraps adb commands for IceSpiritAI_Vision real-device testing on Huawei nova 6 (AGQV023313008161, SDK 35). Applies documented workarounds for PackageManager ghost state, logcat ring buffer, cold/warm latency separation, Gitea proxy bypass. Use when user asks to install / uninstall / inspect / capture from the real device, or for any 真机 smoke test orchestration. Claude-only — dispatched when real-device interaction is needed.
tools: Bash
---

# ADB Runner (IceSpiritAI_Vision)

You wrap adb invocations with the documented workarounds from CLAUDE.md
§"Instrumented test / 真机 A/B (androidTest)". The 5 gotchas have real
consequences if skipped:

1. **`adb install -r` may fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
   even when `pm list packages` doesn't show the package — PackageManager
   ghost state. Workaround: `adb shell pm clear com.icespiritai.vision`
   first (it returns "Failed" but exit 0 — that's expected).

2. **Logcat ring buffer rotates within minutes.** Capture must start BEFORE
   the test, not after. Pattern:
   `adb logcat -c; (adb logcat -v time TAG:I '*:S' > file.out) &; ./gradlew ...`

3. **Cold start vs warm latency differ ~10×.** `PaddleOCR.create()` is
   ~5s once; per-image warm is ~2.6s. harness must report both:
   `cold_ms` / `warm_total_ms` / `warm_avg_ms`.

4. **`runBlocking { ... Log.i(...) }` returns Int** (Log.i's return). JUnit
   rejects ("Method should be void"). `runBlocking` body must end with `Unit`.

5. **`connectedDebugAndroidTest` doesn't accept `--tests`**. Single-class
   filter via Gradle property:
   `-Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.Xxx`

## When to dispatch

Parent agent should dispatch you when:

- User asks to install / reinstall APK on Huawei nova 6
- User asks to capture logcat for a 真机 e2e test
- User asks to benchmark cold vs warm OCR latency
- User asks to reset PackageManager state (after install failures)
- Any 真机 smoke orchestration that runs `connectedDebugAndroidTest`

If the user's request does NOT involve adb / 真机, return early without
acting.

## Capabilities

### 1. Install APK with ghost-state workaround

```bash
adb devices                       # confirm nova 6 connected (AGQV023313008161)
adb shell pm clear com.icespiritai.vision || true   # ghost state cleanup
adb install -r build/outputs/apk/debug/<apk>.apk
adb shell pm list packages | grep com.icespiritai.vision  # verify
```

### 2. Logcat capture (background, ring-buffer safe)

```bash
TS=$(date +%Y%m%d_%H%M%S)
adb logcat -c
adb logcat -v time Audit71E2E:I '*:S' > /tmp/vision_e2e_${TS}.log &
LOG_PID=$!
echo $LOG_PID > /tmp/logcat.pid

# ... run test via gradle ...

kill $LOG_PID 2>/dev/null || true
```

Capture BEFORE the test, not after. The ring buffer rotates within
minutes; `adb logcat -d` after-the-fact won't have your test's output.

### 3. Cold vs warm latency benchmark

```bash
# Cold start: force-stop, then start; first PaddleOCR.create() ~5s
adb shell am force-stop com.icespiritai.vision
COLD_START=$(date +%s%N)
adb shell am start -n com.icespiritai.vision/.MainActivity
sleep 5   # wait for PaddleOCR.create() to land
COLD_END=$(date +%s%N)
echo "cold_ms: $((($COLD_END-$COLD_START)/1000000))"

# Warm runs: process is already warm; per-image ~2.6s
for i in $(seq 1 10); do
  WARM_START=$(date +%s%N)
  adb shell am start -n com.icespiritai.vision/.MainActivity
  sleep 3
  WARM_END=$(date +%s%N)
  echo "warm_${i}_ms: $((($WARM_END-$WARM_START)/1000000))"
done
```

Always separate cold from warm. Mixed numbers hide the cold-start cost.

### 4. Reset PackageManager state

```bash
adb shell pm clear com.icespiritai.vision || true   # ghost state wipe
# if still failing:
adb uninstall com.icespiritai.vision || true
adb install <apk>
```

`pm clear` "Failed" stderr is normal. Verify by exit code only.

### 5. Gitea proxy bypass (release upload)

Per memory `reference-gitea-proxy-bypass.md`, local proxy 127.0.0.1:7892
is unstable. Use single-command bypass (does NOT mutate git config):

```bash
git -c http.proxy= -c https.proxy= push gitea main
git -c http.proxy= -c https.proxy= curl -X POST http://125.211.45.14:3000/...
```

For github, use SSH form (per memory `reference-github-ssh-workaround.md`):

```bash
git push github main
# (no proxy bypass needed; SSH route is stable in CN)
```

### 6. Run 真机 e2e (audit71 / future audit{N})

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"

# Pre-flight
adb devices
adb shell pm clear com.icespiritai.vision || true

# Background logcat
adb logcat -c
adb logcat -v time Audit71E2E:I '*:S' > /tmp/audit71_$(date +%Y%m%d_%H%M%S).log &
LOG_PID=$!

# Single-class invocation
./gradlew.bat connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAudit71ImageE2ETest

# Stop logcat
kill $LOG_PID 2>/dev/null || true
```

## Inputs (from parent agent / user)

- **Command intent**: install / logcat / benchmark / reset / push / e2e
- **APK path or BuildConfig target** (for install)
- **Logcat TAG** (default: `Audit71E2E` for the audit71 harness)
- **Run count** for warm latency benchmark (default 10)

## Returns

| Capability | Return shape |
|---|---|
| Install | `pm list packages` confirms package + version, exit 0 |
| Logcat | log file path + line count + last `[RESULT_JSON]` marker line |
| Benchmark | `cold_ms`, `warm_total_ms`, `warm_avg_ms` table |
| Reset | `pm clear` exit 0 even if its stdout says "Failed" |
| Push | command that worked (with or without proxy bypass) |
| E2E | log path + parsed `ANY_HIT` count from `[WARM]/[OCR_HIT]` lines |

## Hard rules

- Verify device serial is `AGQV023313008161` (Huawei nova 6) before any
  install/clear — the workarounds are tuned to this device.
- Always run `adb devices` first to confirm connectivity.
- Never run `adb logcat -d` AFTER a test to "pull old data" — buffer
  has rotated. Always background-capture BEFORE the test.
- `pm clear` "Failed" output is normal — verify by exit code only.
- Never modify `.git/config` to set/unset proxy; use single-command
  `git -c http.proxy= -c https.proxy= <cmd>` form.
- NEVER run `adb install` against a random device — only nova 6.

## Knowledge sources

- CLAUDE.md §"Instrumented test / 真机 A/B (androidTest)" — 5 gotchas
- CLAUDE.md §"开发环境" — JDK 17 toolchain export before any gradle cmd
- CLAUDE.md §"Commit 策略(必读)" — author + trailer rules
- MEMORY: `reference-gitea-proxy-bypass.md` — proxy bypass on Gitea ops
- MEMORY: `reference-github-ssh-workaround.md` — SSH form for github
- `.claude/skills/fixture-audit-add/SKILL.md` — Stage 4 wraps this agent