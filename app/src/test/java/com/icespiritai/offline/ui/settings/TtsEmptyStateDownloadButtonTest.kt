package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Empty state 下载按钮 UI 断言(spec §7.2 / §8.4)。
 *
 * 验证 [TtsEnginePickerScreen] 在 engines 为空时:
 * 1. 点击"下载引擎"按钮 → onDownloadEngine 回调被触发
 * 2. isDownloading=true + downloadProgress=47 时显示进度文案"下载冰灵 TTS 引擎… 47%"
 *
 * 这两个参数在 Task 10 (picker screen) 已经落地,本 test 为契约 pin。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsEmptyStateDownloadButtonTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tap download engine triggers onDownloadEngine callback`() {
        var downloadClicked = false
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onSelectEngine = {},
                    engines = emptyList(),
                    onDownloadEngine = { downloadClicked = true },
                )
            }
        }
        composeRule.onNodeWithText("下载引擎", useUnmergedTree = true).performClick()
        assertEquals(true, downloadClicked)
    }

    @Test fun `downloading state shows progress text`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onSelectEngine = {},
                    engines = emptyList(),
                    isDownloading = true,
                    downloadProgress = 47,
                )
            }
        }
        composeRule.onNodeWithText("下载冰灵 TTS 引擎… 47%", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}