package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `home idle shows capture and pick buttons`() {
        var captured = 0
        var picked = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                HomeScreenBare(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                )
            }
        }
        composeRule.onNodeWithText("拍照").assertExists()
        composeRule.onNodeWithText("选图").assertExists()
        composeRule.onNodeWithText("拍照").performClick()
        composeRule.onNodeWithText("选图").performClick()
        assert(captured == 1)
        assert(picked == 1)
    }

    @Test
    fun `home idle shows image hint`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                HomeScreenBare(onCapture = {}, onPick = {})
            }
        }
        composeRule.onNodeWithText("请对正图片后点击拍照").assertExists()
    }
}