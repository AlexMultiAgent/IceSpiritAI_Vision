package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeTopBarTtsA11yTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `Idle state has VolumeUp a11y contentDescription`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Idle,
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读识别结果;AI 识别仅供参考", useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test fun `Speaking state has Stop a11y contentDescription`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Speaking,
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("停止朗读", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test fun `InitFailed state has init-failed a11y contentDescription`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.InitFailed("x"),
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读功能不可用,设置中查看详情", useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
