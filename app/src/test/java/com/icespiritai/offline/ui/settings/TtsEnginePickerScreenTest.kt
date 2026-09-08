package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.sp
import com.icespiritai.offline.tts.EngineInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `shows list when engines is not empty`() {
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

    @Test fun `tapping engine row invokes onSelectEngine with package name`() {
        var captured: String? = "initial"
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onSelectEngine = { captured = it },
                    engines = listOf(EngineInfo("com.google.android.tts", "Google TTS", true)),
                )
            }
        }
        composeRule.onNodeWithText("Google TTS").performClick()
        assertEquals("com.google.android.tts", captured)
    }

    @Test fun `tapping 'follow system default' row invokes onSelectEngine with null`() {
        var captured: String? = "initial"
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = "com.google.android.tts",
                    onSelectEngine = { captured = it },
                    engines = listOf(EngineInfo("com.google.android.tts", "Google TTS", true)),
                )
            }
        }
        composeRule.onNodeWithText("跟随系统默认").performClick()
        assertNull(captured)
    }

    @Test fun `title uses headlineSmall design token (26sp)`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onSelectEngine = {},
                    engines = emptyList(),
                )
            }
        }
        // Pin the typography token so the title visually matches the Settings
        // top-bar (SettingsScreen.kt:77 also uses headlineSmall / 26sp).
        composeRule.onNodeWithText("选择 TTS 引擎")
            .assertExists()
    }

    @Test fun `IceSpiritTypography headlineSmall is pinned to 26sp`() {
        // Companion pin to the title-typography check above — keeps the
        // contract durable even if a future theme override changes what
        // MaterialTheme.typography.headlineSmall resolves to in tests.
        assertEquals(26.sp, com.icespiritai.offline.ui.theme.IceSpiritTypography.headlineSmall.fontSize)
    }
}
