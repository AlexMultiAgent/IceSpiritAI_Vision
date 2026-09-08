package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeTopBarTtsTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tts button shows VolumeUp icon when state is Idle and complete`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var speakToggleCount = 0
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage,
                    onSelectTab = {},
                    tabEnabled = true,
                    onOpenSettings = {},
                    ttsState = TtsState.Idle,
                    isAnalysisComplete = true,
                    onSpeakToggle = { speakToggleCount++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读识别结果;AI 识别仅供参考", useUnmergedTree = true,
        ).assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.mainClock.advanceTimeBy(100)
    }

    @Test fun `tts button shows Stop icon when state is Speaking`() {
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

    @Test fun `tts button is disabled when not complete and not InitFailed`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Idle,
                    isAnalysisComplete = false, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读,当前无可朗读结果", useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test fun `tts button shows init failed a11y when InitFailed`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.InitFailed("test"),
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读功能不可用,设置中查看详情", useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /**
     * Bug 1 (v0.1.59) regression pin: when an analysis has just completed
     * (`isAnalysisComplete = true`) and the TTS controller is in [TtsState.Idle]
     * (engine OK, no speech in flight), the 朗读 button must surface as an
     * enabled IconButton — not the greyed-out decorative fallback. The
     * accent border is a visual layer not exposed in the semantics tree
     * (it's a Compose [border] modifier, not a semantics property), so we
     * pin the *enablement* here and defer the accent-border check to a
     * 真机 screenshot.
     */
    @Test fun `ttsIconButton is enabled when isAnalysisComplete is true and ttsState is Idle`() {
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
        ).assertIsEnabled()
    }

    /**
     * Counterpart pin: when no analysis has completed yet
     * (`isAnalysisComplete = false`), the same node must surface as
     * disabled so the visual cue ("没有可朗读的结果") is honest.
     */
    @Test fun `ttsIconButton is disabled when isAnalysisComplete is false and ttsState is Idle`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Idle,
                    isAnalysisComplete = false, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读,当前无可朗读结果", useUnmergedTree = true,
        ).assertIsNotEnabled()
    }
}
