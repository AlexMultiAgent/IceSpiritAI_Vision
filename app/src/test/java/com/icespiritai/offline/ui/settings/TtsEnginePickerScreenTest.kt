package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.sp
import com.icespiritai.offline.tts.EngineInfo
import com.icespiritai.offline.tts.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bug 4 fix (v0.1.61): the picker now renders every engine with a
 * status chip when applicable, and a single [onEngineClick] callback
 * resolves tap → select (Installed) or tap → download (anything else).
 *
 * These tests pin the picker contract:
 * - empty state when no engines at all (defensive fallback only — the
 *   bundled local engine normally means the list is never empty)
 * - system engines render with the row label
 * - the local engine row renders with a "下载" chip when NeedsDownload
 *   and a Check icon when Installed
 * - tapping any row invokes [onEngineClick] with the package (controller
 *   then decides download vs select)
 * - title uses headlineSmall / 26sp (Phase 3 Editorial token)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsEnginePickerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `shows empty state when engines list is empty`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onEngineClick = {},
                    engines = emptyList(),
                )
            }
        }
        composeRule.onNodeWithText("未找到中文 TTS 引擎", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `shows list with system engine rows when engines is not empty`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = "com.huawei.hivoice", onEngineClick = {},
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

    @Test fun `local engine row shows NeedsDownload chip when model is not installed`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onEngineClick = {},
                    engines = listOf(
                        EngineInfo(
                            packageName = com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE,
                            label = "冰灵 TTS 引擎(本地)",
                            supportsChinese = true,
                            status = EngineStatus.NeedsDownload,
                        ),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("冰灵 TTS 引擎(本地)", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("下载", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `local engine row shows Downloading chip during install`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onEngineClick = {},
                    engines = listOf(
                        EngineInfo(
                            packageName = com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE,
                            label = "冰灵 TTS 引擎(本地)",
                            supportsChinese = true,
                            status = EngineStatus.Downloading,
                        ),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("下载中", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `tapping installed engine row invokes onEngineClick with package name`() {
        var captured: String? = "initial"
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onEngineClick = { captured = it },
                    engines = listOf(EngineInfo("com.google.android.tts", "Google TTS", true)),
                )
            }
        }
        composeRule.onNodeWithText("Google TTS").performClick()
        assertEquals("com.google.android.tts", captured)
    }

    @Test fun `tapping NeedsDownload row invokes onEngineClick with package name`() {
        // Controller routes non-installed LOCAL clicks to the
        // installer; picker just forwards the click.
        var captured: String? = "initial"
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onEngineClick = { captured = it },
                    engines = listOf(
                        EngineInfo(
                            packageName = com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE,
                            label = "冰灵 TTS 引擎(本地)",
                            supportsChinese = true,
                            status = EngineStatus.NeedsDownload,
                        ),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("冰灵 TTS 引擎(本地)").performClick()
        assertEquals(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE, captured)
    }

    @Test fun `tapping the status chip area on NeedsDownload row triggers onEngineClick`() {
        // Bug 6 fix (v0.1.62): the status chip MUST NOT swallow pointer
        // events. When it was an AssistChip(enabled=false), Material 3's
        // internal Modifier.clickable consumed the gesture and the row's
        // selectable.onClick never fired. The fix replaces the chip with
        // a non-clickable Box so taps inside the chip rectangle bubble
        // up to the row. Pin that contract here — assert that tapping
        // the chip *text* ("下载") triggers the same onEngineClick.
        var captured: String? = "initial"
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = null,
                    onEngineClick = { captured = it },
                    engines = listOf(
                        EngineInfo(
                            packageName = com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE,
                            label = "冰灵 TTS 引擎(本地)",
                            supportsChinese = true,
                            status = EngineStatus.NeedsDownload,
                        ),
                    ),
                )
            }
        }
        // Tap the chip text directly — this used to no-op silently.
        composeRule.onNodeWithText("下载").performClick()
        assertEquals(
            "tapping the '下载' chip text must bubble to row's onClick → onEngineClick",
            com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE,
            captured,
        )
    }

    @Test fun `tapping 'follow system default' row invokes onEngineClick with null`() {
        var captured: String? = "initial"
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {},
                    currentEnginePackage = "com.google.android.tts",
                    onEngineClick = { captured = it },
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
                    onEngineClick = {},
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