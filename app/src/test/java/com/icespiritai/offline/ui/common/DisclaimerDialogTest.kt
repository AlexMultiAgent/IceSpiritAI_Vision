package com.icespiritai.offline.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DisclaimerDialogTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `dialog shows title and 3 body paragraphs`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DisclaimerDialog(onAcknowledge = {})
            }
        }
        composeRule.onNodeWithText("使用提示", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "本应用通过 OCR 与规则匹配辅助识别广告招牌违规情形",
            substring = true, useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("规则库可能滞后于最新法规", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("请将本应用作为现场辅助工具使用", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("我了解", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `tap ack invokes callback`() {
        var acked = false
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DisclaimerDialog(onAcknowledge = { acked = true })
            }
        }
        composeRule.onNodeWithText("我了解", useUnmergedTree = true).performClick()
        assertTrue(acked)
    }

    @Test fun `dialog does not show confirm button copy changed`() {
        // 单测 pin: button 文本始终是 "我了解"(避免后续误改成"确定"丢失法务语义)
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DisclaimerDialog(onAcknowledge = {})
            }
        }
        composeRule.onNodeWithText("我了解", useUnmergedTree = true).assertIsDisplayed()
    }
}