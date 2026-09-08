package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import android.net.Uri
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResultPanelA11yTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `footer shows when report has zero hits`() {
        val report = ViolationReport(
            imageUri = Uri.EMPTY, ocrText = "some text", hits = emptyList(), timestampMs = 0,
        )
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(report = report)
            }
        }
        composeRule.onNodeWithText("⚠ AI 识别仅供参考,实际以现场判断为准", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test fun `footer does not show when report has hits`() {
        val report = ViolationReport(
            imageUri = Uri.EMPTY, ocrText = "x",
            hits = listOf(RuleHit("r", "100%", "absolute", "广告法 §9", Severity.Violation)),
            timestampMs = 0,
        )
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(report = report)
            }
        }
        composeRule.onNodeWithText("⚠ AI 识别仅供参考,实际以现场判断为准", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
