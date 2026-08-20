package com.icespiritai.offline.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.settings.FakeThemeSettingsSource
import com.icespiritai.offline.settings.SettingsViewModel
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
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
 * ReadyToInstall / Failed) are covered by `SettingsViewModelTest` at the
 * VM layer. End-to-end UI assertions on those branches are deferred until
 * UpdateRepository becomes injectable (Wave 4: P2-5 / P2-6).
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
}