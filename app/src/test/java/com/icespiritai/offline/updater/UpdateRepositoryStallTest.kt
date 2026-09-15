package com.icespiritai.offline.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
 *
 * `MutableStateFlow.value =` dispatches subscriber notifications through
 * `Dispatchers.Main`; without `@Before setMain(...)` / `@After resetMain()`
 * those notifications land on the real Android main looper (or whatever
 * the previous test left behind) and `full-suite` ordering surfaces the
 * `DispatchException`. See CLAUDE.md §"Unit test 踩坑".
 */
class UpdateRepositoryStallTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

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

    private fun testRecord() = DownloadRecord(
        downloadId = "x", url = "http://x", destPath = "/tmp/x",
        bytesWritten = 0, totalBytes = 0, etag = null,
        signerCertSha256 = "", stage = DownloadRecord.DownloadStage.Downloading,
        versionName = "", startedAtEpochMs = 0L,
    )
}
