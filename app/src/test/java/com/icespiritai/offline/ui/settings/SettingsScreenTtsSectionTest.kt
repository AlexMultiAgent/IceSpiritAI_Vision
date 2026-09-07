package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenTtsSectionTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tts section shows title and switch`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = {}, currentEngineLabel = "跟随系统默认",
                    onOpenEnginePicker = {},
                )
            }
        }
        // SettingsScreen is a verticalScroll Column (5 sections; in Robolectric's
        // small viewport the TTS card sits below the fold). Scroll first so the
        // node bounds intersect the parent viewport before assertIsDisplayed().
        composeRule.onNodeWithText("语音播报", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("启用朗读功能", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "ℹ 朗读内容仅供参考,实际合规判断请以现场检查为准。", useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
    }

    @Test fun `engine row shows current engine label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = {}, currentEngineLabel = "HiVoice 语音引擎",
                    onOpenEnginePicker = {},
                )
            }
        }
        composeRule.onNodeWithText("HiVoice 语音引擎", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun `switch off calls onSetTtsEnabled with false`() {
        var captured: Boolean? = null
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = { captured = it },
                    currentEngineLabel = "跟随系统默认", onOpenEnginePicker = {},
                )
            }
        }
        composeRule.onNodeWithText("启用朗读功能", useUnmergedTree = true)
            .performScrollTo().performClick()
        org.junit.Assert.assertEquals(false, captured)
    }
}
