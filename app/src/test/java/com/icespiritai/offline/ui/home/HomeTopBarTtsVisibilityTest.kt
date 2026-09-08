package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
class HomeTopBarTtsVisibilityTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tts button does not render when state is Disabled`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Disabled,
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("朗读", substring = true, useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
