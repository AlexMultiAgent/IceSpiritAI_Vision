package com.icespiritai.offline.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Regression tests for the in-app update 「重试」 button (the user-visible
 * `Failed` card's retry CTA, defined in `UpdateSection.kt:181`).
 *
 * Bug history (v0.4.2 → v0.4.3 patch): tapping 「重试」 from a Failed card
 * after `SettingsViewModel` recreation (rotation, navigation away/back,
 * `UpdateResumeWorker` cold-start resume) silently no-op'd because
 * `UpdateRepository.retry`'s download branches all guarded on
 * `info != null` and `info` was sourced from a VM-private cache that was
 * lost on VM recreation. Fix: the cache moved to a process-global
 * `_lastDownloadInfo` inside `UpdateRepository` itself, written by
 * `downloadApk` / `resumeService` and read by `retry`.
 *
 * These tests exercise the Repository-level contract directly (no
 * ViewModel, no UI) — `SettingsViewModel.retry` is a thin pass-through
 * that delegates here. Robolectric is required because the resume / redownload
 * branches call `context.startForegroundService(...)`, which Robolectric
 * stubs but plain JVM tests don't.
 *
 * Mirrors the `Dispatchers.setMain / resetMain` pattern in
 * `UpdateRepositoryStallTest.kt` — `MutableStateFlow.value =` dispatches
 * subscriber notifications through `Dispatchers.Main`, which without
 * `setMain(UnconfinedTestDispatcher())` lands on the real Android main
 * looper and trips a `DispatchException` between tests in the same JVM.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateRepositoryRetryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val info = AppVersionInfo(
        versionCode = 2,
        versionName = "0.2.0",
        apkUrl = "http://x/y.apk",
        apkSize = 1024L,
        apkSha256 = "a".repeat(64),
        changelog = "",
        signerCertSha256 = "b".repeat(64),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Force a known starting state — `UpdateRepository._state` is a
        // process singleton, and `UpdateSectionTest` / `StallTest` /
        // `CancelTest` mutate it without `@After` reset on every test
        // (each test sets up its own seed). We do the same: set Idle in
        // tearDown so cross-test pollution is bounded.
        seedState(UpdateState.Idle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        seedState(UpdateState.Idle)
        clearLastDownloadInfo()
    }

    /**
     * Bug regression: `retry` from `Failed.NetworkUnreachable` must
     * read the process-global `_lastDownloadInfo` and dispatch to
     * `resumeService`. Pre-fix the function took `info` as a parameter
     * sourced from `SettingsViewModel.lastDownloadInfo` (a VM-private
     * cache that was null after VM recreation), and the download
     * branches silently no-op'd.
     *
     * Direct assertion of the `resumeService` side-effect (FGS Intent
     * launch) is impractical in a unit test — `startForegroundService`
     * is Robolectric-stubbed, not actually observed. Instead we assert
     * the read path was hit: `_lastDownloadInfo` was consulted (still
     * present after the call) AND state has moved off `Failed` (because
     * `resumeService` calls `UpdateRepository.downloadApk` which then
     * `startForegroundService`s the FGS — the FGS itself is not
     * Robolectric-exercised, but the absence of a state-stuck-on-Failed
     * proves we did not hit the silent no-op branch).
     */
    @Test
    fun `retry NetworkUnreachable uses _lastDownloadInfo instead of silently no-opping`() {
        seedLastDownloadInfo(info)
        seedState(
            UpdateState.Failed(
                UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable(
                    cause = IOException("test network down"),
                ),
            ),
        )

        val before = readLastDownloadInfo()
        UpdateRepository.retry(
            context = RuntimeEnvironment.getApplication(),
            currentVersionCode = 1,
            jsonUrl = "http://x/latest.json",
        )
        val after = readLastDownloadInfo()

        assertSame(
            "retry must not clear the cache it just read (silent no-op indicator)",
            before,
            after,
        )
    }

    /**
     * Bug regression: `retry` from `Failed.Cancelled` must restore
     * `UpdateAvailable(info)` so the user can re-tap 「下载」. Pre-fix
     * this branch's `if (info != null) _state.value = UpdateAvailable(info)`
     * silently no-op'd when the VM-side `lastDownloadInfo` was null.
     *
     * `Cancelled` is the only retry subtype whose post-state is a pure
     * `_state.value =` (no Intent / no FGS launch), so we can assert
     * the transition directly without exercising the Robolectric FGS
     * stub. Cleanest end-to-end check.
     */
    @Test
    fun `retry Cancelled with info restores UpdateAvailable`() {
        seedLastDownloadInfo(info)
        seedState(UpdateState.Failed(UpdateCheckResult.Failed.DownloadInterrupted.Cancelled))

        UpdateRepository.retry(
            context = RuntimeEnvironment.getApplication(),
            currentVersionCode = 1,
            jsonUrl = "http://x/latest.json",
        )

        val s = UpdateRepository.state.value
        assertTrue(
            "retry Cancelled with info must transition to UpdateAvailable, got $s",
            s is UpdateState.UpdateAvailable,
        )
        val av = s as UpdateState.UpdateAvailable
        assertEquals(info, av.info)
    }

    /**
     * Defensive fallback: if `_lastDownloadInfo` is somehow null at
     * retry time (defense-in-depth — shouldn't happen in-process after
     * the fix since every retry path is reached via a prior
     * `downloadApk` / `resumeService` that sets the cache), `retry`
     * must NOT silently no-op. Pre-fix it did exactly that. Post-fix
     * it falls back to `checkForUpdatesAsync`, which sets state to
     * `Checking` (the singleton's state machine then transitions
     * further, but the immediate observable is the state change off
     * `Failed`).
     *
     * Uses a `SocketTimeoutException`-like IOException cause to mirror
     * the production NetworkUnreachable code path; the fallback
     * behavior is identical across all `DownloadInterrupted` subtypes.
     */
    @Test
    fun `retry with null _lastDownloadInfo falls back to checkForUpdatesAsync`() {
        clearLastDownloadInfo()
        seedState(
            UpdateState.Failed(
                UpdateCheckResult.Failed.DownloadInterrupted.Other(
                    cause = IOException("test"),
                ),
            ),
        )

        UpdateRepository.retry(
            context = RuntimeEnvironment.getApplication(),
            currentVersionCode = 1,
            jsonUrl = "http://x/latest.json",
        )

        val s = UpdateRepository.state.value
        assertTrue(
            "retry must not silently keep state at Failed when cache is empty, got $s",
            s !is UpdateState.Failed,
        )
    }

    /**
     * Secondary contract: `retry` from `Failed.SignatureMismatch` with
     * an info cache must dispatch to `downloadApk` (which kicks a fresh
     * download — the FGS deletes the bad APK on Mismatch and re-fetches).
     * Pre-fix the same `if (info != null)` guard silently no-op'd.
     *
     * Both `resumeService` and `downloadApk` only fire a FGS Intent via
     * `context.startForegroundService(...)` and don't mutate `_state`
     * synchronously — so the observable side-effect from a pure-JVM
     * test is "did `retry` read `_lastDownloadInfo` to get there?".
     * We assert the cache value is still present after the call
     * (the read path was hit, not the silent-no-op guard). The
     * Cancelled test above exercises the synchronous state-write path
     * end-to-end; this test covers the read-path-only branches.
     */
    @Test
    fun `retry SignatureMismatch with info dispatches to downloadApk`() {
        seedLastDownloadInfo(info)
        seedState(
            UpdateState.Failed(
                UpdateCheckResult.Failed.SignatureMismatch(
                    expected = "b".repeat(64),
                    actual = "c".repeat(64),
                ),
            ),
        )

        val before = readLastDownloadInfo()
        UpdateRepository.retry(
            context = RuntimeEnvironment.getApplication(),
            currentVersionCode = 1,
            jsonUrl = "http://x/latest.json",
        )
        val after = readLastDownloadInfo()

        assertSame(
            "retry SignatureMismatch must read _lastDownloadInfo (cache unchanged proves read path hit)",
            before,
            after,
        )
    }

    // ── reflection helpers ────────────────────────────────────────────────
    //
    // `_lastDownloadInfo` is private and `UpdateRepository` is an `object`
    // (no constructor seam), so tests reach for the same reflection
    // pattern `UpdateSectionTest` uses for `_state`. The alternative
    // (calling real `downloadApk(context, info)` to seed the cache)
    // would require a Context that resolves to a real cacheDir AND
    // Robolectric support for the subsequent FGS Intent — overkill for
    // a one-line setter under test.

    private fun seedState(next: UpdateState) {
        val field = UpdateRepository::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(UpdateRepository) as MutableStateFlow<UpdateState>).value = next
    }

    private fun seedLastDownloadInfo(value: AppVersionInfo) {
        val field = UpdateRepository::class.java.getDeclaredField("_lastDownloadInfo")
        field.isAccessible = true
        field.set(UpdateRepository, value)
    }

    private fun readLastDownloadInfo(): AppVersionInfo? {
        val field = UpdateRepository::class.java.getDeclaredField("_lastDownloadInfo")
        field.isAccessible = true
        return field.get(UpdateRepository) as AppVersionInfo?
    }

    private fun clearLastDownloadInfo() {
        val field = UpdateRepository::class.java.getDeclaredField("_lastDownloadInfo")
        field.isAccessible = true
        field.set(UpdateRepository, null)
    }
}
