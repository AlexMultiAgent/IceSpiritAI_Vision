package com.icespiritai.offline.settings

import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.Config

/**
 * Contract: tapping 「取消」 in the Settings UI must transition UpdateState
 * to Failed.Cancelled IMMEDIATELY (synchronously from the VM), even if the
 * FGS IO coroutine never schedules (cgroup-frozen devices). The FGS
 * `handleCancel` cleanup is best-effort; UI correctness is not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
