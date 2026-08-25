package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins down the three result presentations:
 *  - a photo with no text must say "no text detected", not "no violation";
 *  - text without hits says "no violation" and shows the confidence hint;
 *  - hits render as cards with their severity badge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResultPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val uri = Uri.parse("file:///tmp/x.jpg")

    @Test
    fun noText_showsNotDetectedMessage() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ResultPanel(ViolationReport(uri, "  ", emptyList(), 1L, avgConfidence = 0f))
            }
        }
        composeRule.onNodeWithText(
            "未检测到文字,请上传包含广告文字的海报、招牌或截图",
        ).assertExists()
        composeRule.onNodeWithText("未发现违规用语").assertDoesNotExist()
    }

    @Test
    fun textWithoutHits_showsNoViolationAndLowConfidenceHint() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ResultPanel(
                    ViolationReport(uri, "普通产品介绍", emptyList(), 1L, avgConfidence = 0.3f),
                )
            }
        }
        composeRule.onNodeWithText("识别文字: 普通产品介绍").assertExists()
        composeRule.onNodeWithText("识别置信度较低,结果仅供参考").assertExists()
        composeRule.onNodeWithText("未发现违规用语").assertExists()
    }

    @Test
    fun hits_renderAsCardsWithSeverityBadge() {
        val hit = RuleHit(
            ruleId = "r1",
            matchedText = "最好",
            category = "absolute",
            regulation = "《广告法》第九条第（三）项",
            severity = Severity.Warning,
            lawText = "第九条 广告不得有下列情形：（三）使用“国家级”、“最高级”、“最佳”等用语。",
        )
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(ViolationReport(uri, "全国最好品牌", listOf(hit), 1L, avgConfidence = 0.9f))
            }
        }
        composeRule.onNodeWithText("\"最好\"").assertExists()
        composeRule.onNodeWithText("分类: 绝对化用语").assertExists()
        composeRule.onNodeWithText("依据: 《广告法》第九条第（三）项").assertExists()
        composeRule.onNodeWithText("查看法条原文").performClick()
        composeRule.onNodeWithText("第九条 广告不得有下列情形", substring = true).assertExists()
        composeRule.onNodeWithText("收起法条原文").assertExists()
    }

    @Test
    fun unknownCategory_fallsBackToRawKey() {
        val hit = RuleHit("r1", "示例", "future-category", "《广告法》第九条", Severity.Warning)
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(ViolationReport(uri, "示例文本", listOf(hit), 1L, avgConfidence = 0.9f))
            }
        }
        composeRule.onNodeWithText("分类: future-category").assertExists()
    }
}
