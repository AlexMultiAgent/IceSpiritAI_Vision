# Update Download Stall Recovery (P0)

Date: 2026-09-14
Status: approved (user "你定", 2026-09-14)
Target version: v0.3.1 (bug fix on top of v0.3.0)

## Problem

In-app APK update download can get stuck mid-stream. Two distinct failure
modes observed on Huawei nova 6 (MIUI 13+ / HyperOS-class aggressive ROMs):

1. **Network stall** — server slow segment / captive portal / half-open
   TCP. `ApkDownloader.fetch.ins.read` blocks indefinitely because the
   FGS `HttpURLConnection` has no readTimeout. The 3-attempt retry loop
   only fires on `SocketTimeoutException`, which never arrives.
2. **Cgroup freeze** — MIUI 神隐 / HyperOS PowerGenie / OPPO FrozenApp /
   vivo i 管家 freeze the FGS IO coroutine even though the notification
   stays on-screen. No exception is thrown, no progress is reported.

In both modes, the UI shows `Downloading` with only a 「取消」button. The
existing stall detector (`SettingsViewModel.stallDetectorJob`) fires a
Toast after 5 min but does not transition state. As a separate but
related defect, tapping 「取消」 in the cgroup-frozen case has no effect
because the FGS's `handleCancel` does its work in a `scope.launch` IO
coroutine that also cannot schedule.

User-visible result: a 70 MB download stranded at ~60% with no
recovery path that preserves the partial file.

## Goals

1. Auto-transition `Downloading → Failed.NetworkUnreachable` after 5 min
   of no byte progress, so the user sees the existing 「重试」button.
2. Make 「取消」button immediately responsive, even when the FGS is
   frozen — the UI must not depend on the FGS to update its own state.
3. Existing partial file is preserved across both transitions. The
   retry path uses `Range: bytes=N-` to resume from the on-disk offset,
   not from 0.
4. The FGS byte-stream IO has a real readTimeout so network-level
   stalls surface as `SocketTimeoutException` instead of indefinite
   blocking.

## Non-goals

- New `Stalled` sub-state. Reuse `Failed.NetworkUnreachable`.
- New UI elements. The existing `Failed` card in `UpdateSection.kt`
  already surfaces the right controls.
- Soft-cancel intent. VM-direct state write is simpler and achieves
  the same UX.
- Changing the 5 min stall threshold. The threshold is calibrated for
  captive-portal slow segments (per the `STALL_THRESHOLD_MS` KDoc);
  shortening it would false-positive on legitimate slow networks.
- Changing the retry flow or the `Range` request mechanism.
- Background stall detection when the Settings screen is not visible
  (out of scope — would need a separate process-wide watchdog).

## Design

### Layer 0 — FGS connection timeouts (root cause A: network stall)

**File**: `app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt`
**Change**: `runDownload` openConnection factory adds `.apply { connectTimeout = 15_000; readTimeout = 30_000 }`.

Current (line 188):
```kotlin
openConnection = { URL(record.url).openConnection() as HttpURLConnection },
```

After:
```kotlin
openConnection = {
    (URL(record.url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
    }
},
```

Rationale for the values:
- `readTimeout = 30_000` — long enough to ride out a captive portal
  redirect or a TCP retransmit. With the existing 2+4+8s backoff and
  3-attempt cap in `runDownload`, a persistent stall surfaces as
  `Failed.NetworkUnreachable` in ~104s.
- `connectTimeout = 15_000` — generous for slow DNS / TCP handshake.
- Pattern matches the rest of the repo's downloaders:
  `TtsModelInstaller` uses 30s/60s and 30s/30s, `TtsEngineInstaller`
  uses 30s/60s, `UpdateRepository.checkForUpdates` uses 10s/10s.
  APK download sits closer to the installer pattern (large file,
  occasional slow segment).

Effect on the existing retry loop:
- `ApkDownloader.fetch` already catches `SocketTimeoutException` and
  returns `FetchOutcome.Retryable`.
- `runDownload` already has the 3-attempt cap with backoff.
- After 3 exhausted attempts it already calls
  `UpdateRepository.onDownloadFailed(record, NetworkUnreachable(...))`,
  which writes `Failed.NetworkUnreachable`.
- The 「重试」button in `UpdateSection.kt:170-191` already routes
  through `UpdateRepository.retry` → `resumeService(EXTRA_RESUME=true)`
  → `Range: bytes=N-` + `If-Range: <etag>`.

So the entire downstream path is already correct. This change only
makes the upstream IOException actually fire.

### Layer 1 — Stall detector transitions state (root cause B: cgroup freeze)

**Files**:
- `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt` — new method
- `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt` — stallDetectorJob body

New `UpdateRepository` method:
```kotlin
/**
 * Guarded `Downloading → Failed.NetworkUnreachable` transition.
 *
 * Triggered by [SettingsViewModel.stallDetectorJob] when no byte
 * progress has been reported for [SettingsViewModel.STALL_THRESHOLD_MS].
 *
 * Guard: only transitions if current state is `Downloading` AND
 * `downloadId` matches. Already-Failed / ReadyToInstall / Idle states
 * are no-op to avoid overwriting later progress.
 *
 * Idempotent: second call sees state already at `Failed`, the guard
 * fails, no-op. FGS unstick events (`onDownloadProgress`) overwrite
 * `downloadedBytes`, which resets the stall detector's `lastWritten`
 * and re-arms the 5 min timer before this method ever fires.
 */
fun tryMarkStalledAsFailed(downloadId: String) {
    val cur = _state.value
    if (cur is UpdateState.Downloading && cur.downloadId == downloadId) {
        _state.value = UpdateState.Failed(
            UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable(
                cause = IOException("Download stalled (no progress for 5 minutes)")
            )
        )
    }
}
```

`SettingsViewModel.stallDetectorJob` change (currently at line 187-192):
```kotlin
} else if (!stallSignaled &&
    System.currentTimeMillis() - stallStartedAt >= STALL_THRESHOLD_MS
) {
    stallSignaled = true
    _downloadStallEvents.tryEmit(Unit)
    (updateState.value as? UpdateState.Downloading)?.let {
        UpdateRepository.tryMarkStalledAsFailed(it.downloadId)
    }
}
```

### Layer 2 — Cancel button always responsive (root cause C: cancel FGS path)

**Files**:
- `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt` — new method
- `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt` — cancel() body

New `UpdateRepository` method:
```kotlin
/**
 * Guarded `Downloading → Failed.Cancelled` transition.
 *
 * Triggered by [SettingsViewModel.cancel] immediately after dispatching
 * the FGS cancel intent. The VM writes state directly so the UI does
 * not depend on the FGS's IO coroutine scheduling — on cgroup-frozen
 * devices the FGS `handleCancel` `scope.launch` may never run.
 *
 * Guard: identical to [tryMarkStalledAsFailed].
 *
 * Idempotent with the FGS's own cleanup: when the FGS later wakes up,
 * `handleCancel` calls `cleanup(record)` (deletes partial file +
 * DataStore record) and `onDownloadCancelled(record)` (writes the same
 * `Failed.Cancelled` value). Both writes target the same state, the
 * second is a no-op.
 */
fun markCancelled(downloadId: String) {
    val cur = _state.value
    if (cur is UpdateState.Downloading && cur.downloadId == downloadId) {
        _state.value = UpdateState.Failed(
            UpdateCheckResult.Failed.DownloadInterrupted.Cancelled
        )
    }
}
```

`SettingsViewModel.cancel` change (currently at line 300-305):
```kotlin
fun cancel(context: Context) {
    val infoId = lastDownloadInfo?.let { sha256Short(it.apkUrl + ":" + it.versionCode) }
    val stateId = (updateState.value as? UpdateState.Downloading)?.downloadId
    val downloadId = infoId ?: stateId ?: return
    UpdateRepository.cancel(context.applicationContext, downloadId)
    // cgroup-frozen devices: FGS handleCancel IO coroutine may never
    // schedule. Write state directly so the UI updates immediately
    // instead of waiting for the (potentially dead) FGS to acknowledge.
    UpdateRepository.markCancelled(downloadId)
}
```

### Testability refactor — injectable clock

To enable TDD for the stall detector (Layer 1), `SettingsViewModel`
accepts an optional `clock: () -> Long` constructor parameter:

```kotlin
class SettingsViewModel(
    private val source: ThemeSettingsSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    ...
    // stallDetectorJob uses `clock()` instead of `System.currentTimeMillis()`
}
```

Production factory (`SettingsViewModel.factory`) is unchanged — the
default value applies. Tests inject a controllable clock.

## State machine

```
                              tap Cancel
                                  │
                                  ▼
   tap Download       ┌──────────────────────────► Failed.Cancelled
        │             │                           (no retry button)
        ▼             │
   UpdateAvailable    │ (3 attempts exhaust)
        │             │
        │             │       (5 min no progress)
        │             │
        ▼             ▼
   Downloading ──────────────► Failed.NetworkUnreachable
        ▲                            │
        │                            │ tap retry
        │                            ▼
        │    resumeService(EXTRA_RESUME=true)
        │    Range: bytes=N-
        │                            │
        └────────────────────────────┘
                  (success path)
                       │
                       ▼
                 ReadyToInstall
                       │
                       │ tap install
                       ▼
                 (system installer)
                 → UpdateAvailable
```

The two `Failed.NetworkUnreachable` arrows represent the two paths to
the same state (Layer 0: FGS exhausts 3 retries in ~104s; Layer 1:
stall detector fires at 5 min). Both flow through the same UI card
and the same retry path.

## Test strategy

### New JVM unit tests

1. `app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryStallTest.kt`
   - `tryMarkStalledAsFailed` × 4 cases:
     `noOpWhenStateNotDownloading`, `noOpForDifferentDownloadId`,
     `transitionsToFailedNetworkUnreachable`, `isIdempotent`
   - `markCancelled` × 4 cases (same shape, different failure subtype)

2. `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelCancelTest.kt`
   - `cancel_test_stateTransitionsImmediatelyWithoutFGS` — verifies the
     new VM-level state write
   - `cancel_test_alsoSendsIntentToFGS` — verifies `Repository.cancel`
     was still called (the FGS-side cleanup path is preserved)

3. `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelStallTest.kt`
   - `stallDetectorJob_test_firesStallEvent` (regression — existing
     behavior preserved)
   - `stallDetectorJob_test_alsoTransitionsState` — uses the injected
     clock to advance past the 5 min threshold and asserts state is
     `Failed.NetworkUnreachable`

### Manual smoke test (real device)

On Huawei nova 6 (or any aggressive ROM device):
1. Install current v0.3.0 APK
2. Settings → Update → check for updates → tap 「下载并安装」
3. After ~10s, `adb shell am force-stop com.icespiritai.vision` to
   simulate the cgroup freeze (or use MIUI 神隐 toggle)
4. Re-open Settings, wait up to 5 min
5. Verify: state shows `Failed.NetworkUnreachable`, 「重试」 button
   visible
6. Tap 「重试」, verify download resumes from offset N (logcat tag
   `UpdateDownloadService` shows `Range: bytes=N-`)

Cancel test:
1. Start download, after ~10s tap 「取消」
2. Verify: state transitions to `Failed.Cancelled` within 100ms (no
   waiting on FGS)
3. Verify: no 「重试」 button shown (existing `showRetry` guard)
4. Verify: partial file deleted (after FGS wake-up or app restart,
   `cacheDir/update/` does not contain the downloadId)

## Files to change

| File | Change | Lines |
|---|---|---|
| `updater/UpdateRepository.kt` | Add `tryMarkStalledAsFailed`, `markCancelled` | +30 |
| `settings/SettingsViewModel.kt` | `cancel()` adds `markCancelled`; `stallDetectorJob` adds `tryMarkStalledAsFailed`; constructor accepts optional `clock` | +5 / -0 |
| `updater/service/UpdateDownloadService.kt` | `runDownload` openConnection factory adds timeouts | +3 / -0 |
| `test/.../updater/UpdateRepositoryStallTest.kt` | New — 8 cases | new |
| `test/.../settings/SettingsViewModelCancelTest.kt` | New — 2 cases | new |
| `test/.../settings/SettingsViewModelStallTest.kt` | New — 2 cases | new |

Total: ~38 lines production + ~150 lines tests.

## Rollout

Bug fix → version bump v0.3.0 → v0.3.1 per `feedback-release-hygiene`
(real fix warrants version bump, not bundled with TTS work).

The fix is in the FGS / VM / Repository layer; no UI / assets / string
changes. Build with
`./gradlew.bat assembleRelease -PmodelProfile=ice_ocr_rules`. Triple-SHA
alignment per CLAUDE.md release hygiene.

## Out of scope (explicit)

- Background stall detection when Settings screen is not visible
  (would need a separate process-wide watchdog)
- Notification showing the failure (existing notifier handles
  `Failed` already)
- A "Retry from 0" button (current `Failed.NetworkUnreachable` always
  uses `Range`, which is the desired behavior — preserves the
  partial)
- Changing the 5 min stall threshold
- Soft cancel intent (VM-direct state write is simpler and achieves
  the same UX)
- New `Stalled` sub-state (reuses `Failed.NetworkUnreachable`)
- Modifying the 3-attempt retry policy, backoff schedule, or partial
  file layout
