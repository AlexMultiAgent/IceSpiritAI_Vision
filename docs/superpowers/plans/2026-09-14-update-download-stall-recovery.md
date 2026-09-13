# Update Download Stall Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the in-app APK update download getting stranded at ~60% with no recovery path that preserves the partial file, by adding (a) FGS connection timeouts so the existing 3-attempt retry actually fires, (b) a state transition when the stall detector fires, and (c) a synchronous state write on Cancel so the UI does not depend on the FGS being alive.

**Architecture:** Three thin layers, each fixing one root cause. The FGS byte-stream gets `connectTimeout` / `readTimeout` so `SocketTimeoutException` finally fires (Layer 0). The 5 min stall detector writes `Failed.NetworkUnreachable` directly via a guarded `UpdateRepository.tryMarkStalledAsFailed` (Layer 1). The Cancel button writes `Failed.Cancelled` synchronously from the VM via `UpdateRepository.markCancelled` instead of waiting for the FGS's IO coroutine (Layer 2). All three reuse the existing 「重试」 button + `Range: bytes=N-` resume flow — no new UI, no new state, no new intent.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3, Coroutines 1.x (`viewModelScope` + `Dispatchers.IO`), Android FGS (`Intent` + `startForegroundService`), JUnit 4 + `kotlinx-coroutines-test` + Robolectric for Context-stubbing.

**Reference spec:** `docs/superpowers/specs/2026-09-14-update-download-stall-recovery-design.md`

---

## File map

| File | Responsibility | Status |
|---|---|---|
| `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt` | Add `tryMarkStalledAsFailed`, `markCancelled` guarded transitions | modify |
| `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt` | Add optional `clock` ctor param; `cancel()` calls `markCancelled`; `stallDetectorJob` calls `tryMarkStalledAsFailed` | modify |
| `app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt` | `runDownload` openConnection factory adds `connectTimeout=15_000` / `readTimeout=30_000` | modify |
| `app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryStallTest.kt` | 8 JVM cases (4 per method) | new |
| `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelCancelTest.kt` | 2 Robolectric cases | new |
| `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelStallTest.kt` | 2 JVM cases (uses injected clock) | new |

---

## Task 1: `tryMarkStalledAsFailed` in `UpdateRepository`

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryStallTest.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt`

- [ ] **Step 1: Write the failing test for `tryMarkStalledAsFailed`**

Create `UpdateRepositoryStallTest.kt` with the 4 cases for the first method. The `UpdateRepository._state` is a process singleton — each test sets it explicitly via the public `onDownloadProgress` / `onDownloadCancelled` seam, no `@Before` reset needed.

```kotlin
package com.icespiritai.offline.updater

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Guarded state transitions for the in-app update download. See
 * [docs/superpowers/specs/2026-09-14-update-download-stall-recovery-design.md]
 * §"Layer 1 / Layer 2" for the contract.
 *
 * UpdateRepository._state is a process singleton. Each test forces a
 * known starting state via the public onDownloadProgress / onDownloadCancelled
 * entry points — no @Before reset needed because the last test's state
 * is overwritten before the next test asserts.
 */
class UpdateRepositoryStallTest {

    @Test
    fun `tryMarkStalledAsFailed transitions Downloading to Failed NetworkUnreachable`() {
        UpdateRepository.onDownloadProgress("abc123", 1000L, 10000L)

        UpdateRepository.tryMarkStalledAsFailed("abc123")

        val s = UpdateRepository.state.value
        assertTrue("expected Failed, got $s", s is UpdateState.Failed)
        val r = (s as UpdateState.Failed).result
        assertTrue(
            "expected NetworkUnreachable, got $r",
            r is UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable,
        )
    }

    @Test
    fun `tryMarkStalledAsFailed is no-op when state is not Downloading`() {
        // State starts as Idle (or whatever the previous test left it in).
        // Force to a non-Downloading state.
        UpdateRepository.onDownloadVerified(
            record = testRecord(),
            result = VerifierResult.Mismatch(expected = "a".repeat(64), actual = "b".repeat(64)),
        )

        val before = UpdateRepository.state.value
        UpdateRepository.tryMarkStalledAsFailed("any-id")

        assertTrue(before === UpdateRepository.state.value)
    }

    @Test
    fun `tryMarkStalledAsFailed is no-op when downloadId does not match`() {
        UpdateRepository.onDownloadProgress("real-id", 1000L, 10000L)

        UpdateRepository.tryMarkStalledAsFailed("wrong-id")

        val s = UpdateRepository.state.value
        assertTrue("guard must reject mismatched id, got $s", s is UpdateState.Downloading)
        assertEquals("real-id", (s as UpdateState.Downloading).downloadId)
    }

    @Test
    fun `tryMarkStalledAsFailed is idempotent`() {
        UpdateRepository.onDownloadProgress("abc123", 1000L, 10000L)

        UpdateRepository.tryMarkStalledAsFailed("abc123")
        val first = UpdateRepository.state.value

        // Second call should be a no-op (state already Failed).
        UpdateRepository.tryMarkStalledAsFailed("abc123")
        val second = UpdateRepository.state.value

        assertTrue(first === second)
    }

    private fun testRecord() = DownloadRecord(
        downloadId = "x", url = "http://x", destPath = "/tmp/x",
        bytesWritten = 0, totalBytes = 0, etag = null,
        signerCertSha256 = "", stage = DownloadRecord.DownloadStage.Downloading,
        versionName = "", startedAtEpochMs = 0L,
    )
}
```

Add missing imports at the top: `assertEquals` from `org.junit.Assert.assertEquals`.

- [ ] **Step 2: Run the test, see it fail**

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
cd /d/GitHub/IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.updater.UpdateRepositoryStallTest"
```

Expected: compile error — `tryMarkStalledAsFailed` does not exist on `UpdateRepository`.

- [ ] **Step 3: Implement `tryMarkStalledAsFailed` in `UpdateRepository`**

In `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt`, add the import (if not already present) and the new method. Place it adjacent to the existing `onDownloadCancelled` for grouping.

```kotlin
import java.io.IOException
```

```kotlin
    /**
     * Guarded `Downloading → Failed.NetworkUnreachable` transition.
     *
     * Triggered by [com.icespiritai.offline.settings.SettingsViewModel.stallDetectorJob]
     * when no byte progress has been reported for STALL_THRESHOLD_MS (5 min).
     * Writes state directly from the VM so the UI does not depend on the
     * FGS being alive — on cgroup-frozen devices the FGS IO coroutine may
     * never schedule, and the existing stall Toast alone is not a recovery
     * path that preserves the partial file.
     *
     * Guard: only transitions if current state is `Downloading` AND
     * `downloadId` matches. Already-Failed / ReadyToInstall / Idle states
     * are no-op to avoid overwriting later progress. FGS unstick events
     * (`onDownloadProgress`) overwrite `downloadedBytes`, which resets the
     * stall detector's `lastWritten` and re-arms the 5 min timer before
     * this method ever fires.
     *
     * Idempotent: second call sees state already at `Failed`, the guard
     * fails, no-op.
     */
    fun tryMarkStalledAsFailed(downloadId: String) {
        val cur = _state.value
        if (cur is UpdateState.Downloading && cur.downloadId == downloadId) {
            _state.value = UpdateState.Failed(
                UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable(
                    cause = IOException("Download stalled (no progress for 5 minutes)"),
                ),
            )
        }
    }
```

- [ ] **Step 4: Run the test, see it pass**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.updater.UpdateRepositoryStallTest"
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add \
  app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt \
  app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryStallTest.kt
git commit -m "feat(updater): tryMarkStalledAsFailed guarded Downloading -> Failed.NetworkUnreachable"
```

No `Co-Authored-By:` trailer (project hook + hookify rule will block).

---

## Task 2: `markCancelled` in `UpdateRepository`

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryStallTest.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt`

- [ ] **Step 1: Append the 4 failing test cases to `UpdateRepositoryStallTest.kt`**

Append after the closing `}` of `testRecord` (at end of file), the 4 cases for `markCancelled`. Each is structurally identical to the `tryMarkStalledAsFailed` tests but expects the `Failed.DownloadInterrupted.Cancelled` subtype.

```kotlin
    @Test
    fun `markCancelled transitions Downloading to Failed Cancelled`() {
        UpdateRepository.onDownloadProgress("abc123", 1000L, 10000L)

        UpdateRepository.markCancelled("abc123")

        val s = UpdateRepository.state.value
        assertTrue("expected Failed, got $s", s is UpdateState.Failed)
        val r = (s as UpdateState.Failed).result
        assertTrue(
            "expected Cancelled, got $r",
            r is UpdateCheckResult.Failed.DownloadInterrupted.Cancelled,
        )
    }

    @Test
    fun `markCancelled is no-op when state is not Downloading`() {
        UpdateRepository.onDownloadVerified(
            record = testRecord(),
            result = VerifierResult.Mismatch(expected = "a".repeat(64), actual = "b".repeat(64)),
        )

        val before = UpdateRepository.state.value
        UpdateRepository.markCancelled("any-id")

        assertTrue(before === UpdateRepository.state.value)
    }

    @Test
    fun `markCancelled is no-op when downloadId does not match`() {
        UpdateRepository.onDownloadProgress("real-id", 1000L, 10000L)

        UpdateRepository.markCancelled("wrong-id")

        val s = UpdateRepository.state.value
        assertTrue("guard must reject mismatched id, got $s", s is UpdateState.Downloading)
        assertEquals("real-id", (s as UpdateState.Downloading).downloadId)
    }

    @Test
    fun `markCancelled is idempotent`() {
        UpdateRepository.onDownloadProgress("abc123", 1000L, 10000L)

        UpdateRepository.markCancelled("abc123")
        val first = UpdateRepository.state.value

        UpdateRepository.markCancelled("abc123")
        val second = UpdateRepository.state.value

        assertTrue(first === second)
    }
```

- [ ] **Step 2: Run the test, see it fail (compile error)**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.updater.UpdateRepositoryStallTest"
```

Expected: compile error — `markCancelled` does not exist on `UpdateRepository`.

- [ ] **Step 3: Implement `markCancelled` in `UpdateRepository`**

Insert immediately after `tryMarkStalledAsFailed` (Task 1, Step 3):

```kotlin
    /**
     * Guarded `Downloading → Failed.Cancelled` transition.
     *
     * Triggered by [com.icespiritai.offline.settings.SettingsViewModel.cancel]
     * immediately after dispatching the FGS cancel intent. The VM writes
     * state directly so the UI does not depend on the FGS's IO coroutine
     * scheduling — on cgroup-frozen devices the FGS `handleCancel`
     * `scope.launch` may never run, leaving the UI stuck at `Downloading`.
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
                UpdateCheckResult.Failed.DownloadInterrupted.Cancelled,
            )
        }
    }
```

- [ ] **Step 4: Run the test, see it pass**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.updater.UpdateRepositoryStallTest"
```

Expected: 8 tests pass (4 + 4).

- [ ] **Step 5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add \
  app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt \
  app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryStallTest.kt
git commit -m "feat(updater): markCancelled guarded Downloading -> Failed.Cancelled"
```

---

## Task 3: `SettingsViewModel.cancel` writes state synchronously (with clock refactor)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelCancelTest.kt`

- [ ] **Step 1: Add optional `clock: () -> Long` parameter to `SettingsViewModel` constructor**

In `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`, change the class signature (line 54) and the `stallDetectorJob` body (line 166, 185, 188) to use the injected clock instead of `System.currentTimeMillis()`. The factory (`SettingsViewModel.factory`) does NOT need to change — it calls `SettingsViewModel(repository)` which uses the default clock.

Class declaration (line 54):

```kotlin
class SettingsViewModel(
    private val source: ThemeSettingsSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
```

Inside `stallDetectorJob` (the `while (true)` block), replace each `System.currentTimeMillis()` with `clock()`. There are two call sites in the body:
- Line 185: `stallStartedAt = System.currentTimeMillis()` → `stallStartedAt = clock()`
- Line 188: `System.currentTimeMillis() - stallStartedAt` → `clock() - stallStartedAt`

Do not change anything else in the file in this step.

- [ ] **Step 2: Run existing tests to confirm clock refactor is non-breaking**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelTest"
```

Expected: all existing tests still pass. The default `clock = System::currentTimeMillis` keeps production behavior identical.

- [ ] **Step 3: Write the failing test for `SettingsViewModel.cancel` state write**

Create `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelCancelTest.kt`. The test needs a real `Context` (the `cancel` method calls `context.applicationContext`); Robolectric provides one without requiring a device. Use the same `UnconfinedTestDispatcher` / `runTest(dispatcher)` pattern as `SettingsViewModelTest`.

```kotlin
package com.icespiritai.offline.settings

import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import com.icespiritai.offline.updater.UpdateCheckResult
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract: tapping 「取消」 in the Settings UI must transition UpdateState
 * to Failed.Cancelled IMMEDIATELY (synchronously from the VM), even if the
 * FGS IO coroutine never schedules (cgroup-frozen devices). The FGS
 * `handleCancel` cleanup is best-effort; UI correctness is not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelCancelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancel transitions state to Failed Cancelled without waiting on FGS`() = runTest(dispatcher) {
        val themeBacking = MutableStateFlow(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(FakeThemeSettingsSource(themeBacking))
        UpdateRepository.onDownloadProgress("test-id", 1000L, 10000L)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        vm.cancel(context)

        val s = UpdateRepository.state.value
        assertTrue("expected Failed, got $s", s is UpdateState.Failed)
        val r = (s as UpdateState.Failed).result
        assertTrue(
            "expected Cancelled, got $r",
            r is UpdateCheckResult.Failed.DownloadInterrupted.Cancelled,
        )
    }

    @Test
    fun `cancel is no-op when state is not Downloading`() = runTest(dispatcher) {
        val themeBacking = MutableStateFlow(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(FakeThemeSettingsSource(themeBacking))
        // Force state to UpdateAvailable (not Downloading) — lastDownloadInfo
        // is also null, so the cancel id resolution returns no-op.
        UpdateRepository.onDownloadProgress("any-id", 1000L, 10000L)
        // Transition away from Downloading so the markCancelled guard fails.
        // We use onDownloadVerified with Mismatch to move to Failed.SignatureMismatch.
        UpdateRepository.onDownloadVerified(
            record = com.icespiritai.offline.updater.DownloadRecord(
                downloadId = "any-id", url = "http://x", destPath = "/tmp/x",
                bytesWritten = 0, totalBytes = 0, etag = null,
                signerCertSha256 = "", stage = com.icespiritai.offline.updater.DownloadRecord.DownloadStage.Downloading,
                versionName = "", startedAtEpochMs = 0L,
            ),
            result = com.icespiritai.offline.updater.VerifierResult.Mismatch(
                expected = "a".repeat(64), actual = "b".repeat(64),
            ),
        )

        val before = UpdateRepository.state.value
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        vm.cancel(context)

        assertTrue("state must not change, was ${before::class.simpleName} now ${UpdateRepository.state.value::class.simpleName}",
            before === UpdateRepository.state.value)
    }
}
```

Note: `RuleTab` import is included for parity with `FakeThemeSettingsSource` constructor — confirm by reading `app/src/test/java/com/icespiritai/offline/settings/FakeThemeSettingsSource.kt` (current shape at time of writing: `class FakeThemeSettingsSource(themeBacking: MutableStateFlow<ThemeMode>, visibleFeaturesBacking: MutableStateFlow<Set<RuleTab>> = MutableStateFlow(RuleTab.entries.toSet()))`); if the visible-features backing is required, pass it explicitly.

- [ ] **Step 4: Run the test, see it fail (assertion fail)**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelCancelTest"
```

Expected: 1 fail (state is still `Downloading`, not `Failed.Cancelled`) + 1 pass (the no-op case). If both fail, check that `FakeThemeSettingsSource` constructor signature matches.

- [ ] **Step 5: Update `SettingsViewModel.cancel` to call `markCancelled`**

In `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`, change the `cancel` method (currently line 300-305):

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

- [ ] **Step 6: Run the test, see it pass**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelCancelTest"
```

Expected: 2 tests pass.

- [ ] **Step 7: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add \
  app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt \
  app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelCancelTest.kt
git commit -m "feat(settings): cancel writes state synchronously (Layer 2)"
```

---

## Task 4: `SettingsViewModel.stallDetectorJob` writes state via `tryMarkStalledAsFailed`

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelStallTest.kt`

- [ ] **Step 1: Write the failing test using the injected clock**

Create `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelStallTest.kt`. The test injects a fake `clock: () -> Long`, sets `Downloading` state, advances the fake clock past the 5 min stall threshold + 30 s poll interval, runs `advanceUntilIdle()` to let the coroutine wake, and asserts the state transitioned to `Failed.NetworkUnreachable`.

```kotlin
package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import com.icespiritai.offline.updater.UpdateCheckResult
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract: the 5 min stall detector (SettingsViewModel.STALL_THRESHOLD_MS)
 * must transition UpdateState from Downloading to Failed.NetworkUnreachable
 * so the UI shows the 「重试」 button. Pre-fix the detector only fired a
 * Toast — no functional recovery path.
 *
 * The injected `clock: () -> Long` parameter on SettingsViewModel (added
 * in Task 3 Step 1) is what makes this testable. The production factory
 * uses System::currentTimeMillis as the default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelStallTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stall detector transitions state to Failed NetworkUnreachable after threshold`() = runTest(dispatcher) {
        var now = 0L
        val themeBacking = MutableStateFlow(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(FakeThemeSettingsSource(themeBacking), clock = { now })
        UpdateRepository.onDownloadProgress("stall-id", 1000L, 10000L)

        // Advance fake clock past STALL_THRESHOLD_MS (5 min) + one poll interval (30 s).
        // The stall detector first sees the Downloading transition at now=0,
        // records lastWritten + stallStartedAt, then delays 30 s. When the
        // delay completes, the clock has already advanced past the 5 min
        // threshold, so the next iteration's check fires.
        now = 5L * 60L * 1000L + 30_000L + 1L
        advanceUntilIdle()

        val s = UpdateRepository.state.value
        assertTrue("expected Failed, got $s", s is UpdateState.Failed)
        val r = (s as UpdateState.Failed).result
        assertTrue(
            "expected NetworkUnreachable, got $r",
            r is UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable,
        )
    }

    @Test
    fun `stall detector does not transition state when progress is moving`() = runTest(dispatcher) {
        var now = 0L
        val themeBacking = MutableStateFlow(ThemeMode.SYSTEM)
        val vm = SettingsViewModel(FakeThemeSettingsSource(themeBacking), clock = { now })
        UpdateRepository.onDownloadProgress("moving-id", 0L, 10000L)
        // Allow the stall detector to record the initial Downloading transition.
        advanceUntilIdle()

        // Bump bytesWritten every 1 s (well under the 5 min threshold).
        // After 4 min of progress, state must still be Downloading.
        for (second in 1..240) {
            now = second * 1000L
            UpdateRepository.onDownloadProgress("moving-id", now, 10000L)
            advanceUntilIdle()
        }

        val s = UpdateRepository.state.value
        assertTrue(
            "state must remain Downloading with moving progress, got $s",
            s is UpdateState.Downloading,
        )
    }
}
```

- [ ] **Step 2: Run the test, see it fail (assertion fail)**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelStallTest"
```

Expected: 1 fail (state is still `Downloading`, not `Failed.NetworkUnreachable`) + 1 pass (the moving-progress case). If the moving-progress test fails, the loop pump rate may need tuning — but the contract is clear: as long as `onDownloadProgress` runs at least once per `STALL_THRESHOLD_MS - STALL_POLL_INTERVAL_MS`, the threshold check never fires.

- [ ] **Step 3: Wire `stallDetectorJob` to call `tryMarkStalledAsFailed`**

In `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`, change the threshold-firing branch (currently around line 187-192). The exact line numbers may have shifted by Task 3 Step 5 (cancel changes) — locate the `} else if (!stallSignaled &&` block.

Before:
```kotlin
            } else if (!stallSignaled &&
                clock() - stallStartedAt >= STALL_THRESHOLD_MS
            ) {
                stallSignaled = true
                _downloadStallEvents.tryEmit(Unit)
            }
```

After:
```kotlin
            } else if (!stallSignaled &&
                clock() - stallStartedAt >= STALL_THRESHOLD_MS
            ) {
                stallSignaled = true
                _downloadStallEvents.tryEmit(Unit)
                // Layer 1: write Failed.NetworkUnreachable directly so the
                // UI shows the 「重试」 button. Guarded inside the Repository
                // method — if state has moved on (FGS finished, or another
                // transition fired) the call is a no-op.
                (updateState.value as? UpdateState.Downloading)?.let {
                    UpdateRepository.tryMarkStalledAsFailed(it.downloadId)
                }
            }
```

- [ ] **Step 4: Run the test, see it pass**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelStallTest"
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add \
  app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt \
  app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelStallTest.kt
git commit -m "feat(settings): stall detector writes Failed.NetworkUnreachable (Layer 1)"
```

---

## Task 5: FGS connection timeouts in `UpdateDownloadService`

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt`

No new test (per spec §"Test strategy" — mechanical `.apply { ... }` two-liner, the existing `ApkDownloaderTest` retry path covers the integration; ROI of a `URL.setURLStreamHandlerFactory` mock is low).

- [ ] **Step 1: Add `connectTimeout` / `readTimeout` to the FGS openConnection factory**

In `app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt`, find `ApkDownloader.fetch(` call inside `runDownload` (around line 187-188). Change the `openConnection` lambda.

Before:
```kotlin
            val outcome = ApkDownloader.fetch(
                openConnection = { URL(record.url).openConnection() as HttpURLConnection },
                destFile = File(record.destPath),
                resumeFrom = resumeOffset,
                etag = lastEtag,
                onProgress = { written ->
```

After:
```kotlin
            val outcome = ApkDownloader.fetch(
                // Layer 0: connection timeouts so the 3-attempt retry loop in
                // runDownload actually fires on a hung server. Without these
                // the Android-default readTimeout=0 leaves ins.read blocking
                // forever on a slow segment / half-open TCP / cgroup freeze.
                // connectTimeout=15 s, readTimeout=30 s — same pattern as
                // TtsModelInstaller (30s/60s) and TtsEngineInstaller (30s/60s).
                openConnection = {
                    (URL(record.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                },
                destFile = File(record.destPath),
                resumeFrom = resumeOffset,
                etag = lastEtag,
                onProgress = { written ->
```

- [ ] **Step 2: Run the full test suite to confirm no regression**

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
cd /d/GitHub/IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest
```

Expected: all tests pass. The FGS path is not directly tested in JVM (it requires Android Service plumbing), so this is a smoke check that the surrounding code still compiles and nothing else broke.

If `apkSigner / cert-pin` related tests fail, check that no other code path that constructs `HttpURLConnection` was missed — but this is a single call site in `runDownload`.

- [ ] **Step 3: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt
git commit -m "feat(updater): FGS openConnection sets connectTimeout=15s, readTimeout=30s (Layer 0)"
```

---

## Task 6: Final verification

- [ ] **Step 1: Run the full test suite once more**

```bash
./gradlew.bat testDebugUnitTest
```

Expected: all tests pass. Specifically check:
- `com.icespiritai.offline.updater.UpdateRepositoryStallTest` — 8 cases
- `com.icespiritai.offline.settings.SettingsViewModelCancelTest` — 2 cases
- `com.icespiritai.offline.settings.SettingsViewModelStallTest` — 2 cases
- `com.icespiritai.offline.settings.SettingsViewModelTest` — pre-existing cases, no regression from clock refactor
- `com.icespiritai.offline.updater.ApkDownloaderTest` — pre-existing cases, no regression from FGS timeouts (the test injects a fake `openConnection` factory so production code path is not exercised)
- `com.icespiritai.offline.updater.UpdateRepositoryCheckTest` / `UpdateRepositoryDownloadTest` — pre-existing cases, no regression

- [ ] **Step 2: Verify no `Co-Authored-By:` trailer in any of the 5 commits**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git log --format='%B' -5 | grep -i 'Co-Authored-By' && echo "TRAILER FOUND — amend offending commit" || echo "OK no trailers"
```

Expected: `OK no trailers`. If a trailer is found, amend the offending commit (the project `post-tool-use.js` hook should have blocked it, but verify).

- [ ] **Step 3: Verify file paths match the spec's "Files to change" table**

```bash
git diff --stat HEAD~5..HEAD
```

Expected: 6 files changed, matching the spec table exactly (3 production + 3 test). No `git add -A` accidents (e.g., `vision-latest.json` should NOT be in the diff).

- [ ] **Step 4: Note for v0.3.1 release**

The implementation is complete. Release steps (out of scope for this plan, per `feedback-release-hygiene`):
1. Bump `versionCode 70 → 71` in `app/build.gradle.kts`
2. Top entry in `user-changelog.md`
3. `./icevision-release` skill: pre-flight + 4-step pipeline + triple-SHA
4. Tag `v0.3.1` + push `latest` ref

---

## Spec coverage check

| Spec section | Implementing task |
|---|---|
| §"Layer 0 — FGS connection timeouts" | Task 5 |
| §"Layer 1 — Stall detector transitions state" (new method) | Task 1 |
| §"Layer 1 — Stall detector transitions state" (VM call) | Task 4 |
| §"Layer 2 — Cancel button always responsive" (new method) | Task 2 |
| §"Layer 2 — Cancel button always responsive" (VM call) | Task 3 |
| §"Testability refactor — injectable clock" | Task 3 Step 1 |
| §"State machine" | (no code; documented for review) |
| §"Test strategy" (Repository cases) | Task 1 + Task 2 |
| §"Test strategy" (Cancel VM case) | Task 3 |
| §"Test strategy" (Stall VM case) | Task 4 |
| §"Test strategy" (FGS connection timeouts, no test) | Task 5 |
| §"Out of scope" (manual smoke test) | Task 6 Step 4 (note only) |

## Self-review

- No "TBD" / "TODO" / "implement later" in the plan.
- Method signatures consistent across tasks: `tryMarkStalledAsFailed(downloadId: String)`, `markCancelled(downloadId: String)`, `SettingsViewModel(clock: () -> Long = System::currentTimeMillis)`.
- All commands are runnable on the user's Windows environment with the explicit `JAVA_HOME` export (matches CLAUDE.md JDK 17 requirement).
- `git add` uses specific paths in every step (matches project `pre-tool-use.js` hook).
- No `Co-Authored-By:` trailer in any commit message (matches project `post-tool-use.js` + hookify rules).
- Robolectric used only for the Context-requiring test in Task 3; pure-JVM tests elsewhere (per existing `SettingsViewModelTest` pattern).
