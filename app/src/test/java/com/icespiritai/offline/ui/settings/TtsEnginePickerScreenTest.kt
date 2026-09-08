package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.tts.EngineInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsEnginePickerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `shows empty state when engines list is empty`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onSelectEngine = {},
                    engines = emptyList(),
                )
            }
        }
        composeRule.onNodeWithText("未找到中文 TTS 引擎", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("下载引擎", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `shows radio list when engines is not empty`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = "com.huawei.hivoice", onSelectEngine = {},
                    engines = listOf(
                        EngineInfo("com.huawei.hivoice", "荣耀 AI 语音引擎", true),
                        EngineInfo("com.google.android.tts", "Google TTS", true),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("跟随系统默认", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("荣耀 AI 语音引擎", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Google TTS", useUnmergedTree = true).assertIsDisplayed()
    }
}
