package com.icespiritai.offline.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.settings.FakeThemeSettingsSource
import com.icespiritai.offline.settings.SettingsViewModel
import com.icespiritai.offline.ui.theme.ThemeMode
import com.icespiritai.offline.updater.UpdateCheckResult
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [UpdateSection] — the update-status card on the
 * Settings screen.
 *
 * SettingsViewModel is `final` and wires `updateState` to the
 * `UpdateRepository.state` singleton, so we can't fully substitute the
 * state without refactoring. This test exercises the always-rendered
 * section title + the Idle-state "检查更新" button (which the default
 * `UpdateRepository.state` emits before any refresh attempt).
 *
 * The other states (Checking / UpToDate / UpdateAvailable / Downloading /
 * ReadyToInstall) are covered by `SettingsViewModelTest` at the VM
 * layer. The Failed branch (Bug 7 regression) is asserted here end-to-end
 * because the failureLabel / retry-button logic lives in the Compose
 * layer, not in the VM. We mutate the process-singleton state via
 * reflection (`@VisibleForTesting` shape, not yet promoted to a public
 * test seam) and restore Idle in `@After` so subsequent tests aren't
 * poisoned.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun idleViewModel(): SettingsViewModel {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val source = FakeThemeSettingsSource(backing)
        return SettingsViewModel(source)
    }

    /**
     * Replace [UpdateRepository.state] with [next] via reflection on the
     * private `_state` MutableStateFlow. Returns the previous value so
     * the test can restore it (otherwise the singleton would leak the
     * synthetic Failed state into other tests in the same JVM).
     */
    private fun setRepoState(next: UpdateState): UpdateState {
        val field = UpdateRepository::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val flow = field.get(UpdateRepository) as MutableStateFlow<UpdateState>
        val previous = flow.value
        flow.value = next
        return previous
    }

    @org.junit.After
    fun restoreRepoState() {
        // Best-effort: any test that injected a synthetic state must
        // restore before the next test, or the singleton stays poisoned.
        setRepoState(UpdateState.Idle)
    }

    @Test
    fun `renders the section title`() {
        composeRule.setContent {
            UpdateSection(viewModel = idleViewModel(), onOpenUpdateDetail = {})
        }
        composeRule.onNodeWithText("更新").assertExists()
    }

    @Test
    fun `Idle state surfaces the check-for-update button`() {
        // Default UpdateRepository.state is Idle before any user action.
        // We deliberately do NOT click the button because it would fire a
        // real HTTP request to the Gitea release server.
        composeRule.setContent {
            UpdateSection(viewModel = idleViewModel(), onOpenUpdateDetail = {})
        }
        composeRule.onNodeWithText("检查更新").assertExists()
    }

    /**
     * Bug 7 regression: silent update check failure was reported but
     * [UpdateSection] already surfaces Failed correctly (error message +
     * retry button, except for explicit user-cancellation). Pin the
     * behaviour here so a future refactor that drops the Failed branch
     * (or hides the retry CTA behind a no-op gating check) is caught at
     * CI rather than at release.
     */
    @Test
    fun `Failed NoNetwork state shows error label and retry button`() {
        setRepoState(UpdateState.Failed(UpdateCheckResult.Failed.NoNetwork))
        composeRule.setContent {
            UpdateSection(viewModel = idleViewModel(), onOpenUpdateDetail = {})
        }
        // NoNetwork → R.string.update_failed_no_network
        composeRule.onNodeWithText("无法连接服务器,请检查网络").assertExists()
        // Non-Cancelled Failed branches must surface the 重试 button.
        composeRule.onNodeWithText("重试").assertExists()
    }

    /**
     * User-reported bug (2026-09-16): tapping 「取消」 during a download
     * landed the user on a 「已取消」 card with no recovery path. They had
     * to close the app and reopen to retry. Fix: every Failed branch —
     * including explicit user cancellation — now surfaces 「重试」. For
     * the Cancelled branch the retry restores UpdateAvailable so the
     * user can re-tap 「下载」, or falls back to a fresh metadata check
     * if `_lastDownloadInfo` was lost.
     */
    @Test
    fun `Failed Cancelled state shows cancelled label and retry button`() {
        setRepoState(
            UpdateState.Failed(UpdateCheckResult.Failed.DownloadInterrupted.Cancelled),
        )
        composeRule.setContent {
            UpdateSection(viewModel = idleViewModel(), onOpenUpdateDetail = {})
        }
        composeRule.onNodeWithText("已取消").assertExists()
        // 重试 button must show — the recovery path now lives there.
        composeRule.onNodeWithText("重试").assertExists()
    }
}