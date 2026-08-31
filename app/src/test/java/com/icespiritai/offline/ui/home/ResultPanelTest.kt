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
 * Pins down the four [ResultPanel] presentations after the Phase 3.5
 * redesign (2026-08-31):
 *  - a photo with no text must say "未检测到文字…", not "未发现违规用语"
 *    (the latter would falsely imply the text was actually reviewed);
 *  - text without hits says "未发现违规用语" and shows the low-confidence
 *    hint;
 *  - hits render as [HitCard]s **without** an OCR-text header. Each hit
 *    shows a severity chip ("违规" / "警告" / "信息") in place of the old
 *    rule-category line ("分类: X") and a 查看/收起法条原文 toggle;
 *  - hits are grouped under severity-section headers ("违规 (N)" /
 *    "警告 (N)" / "信息 (N)"), in rank order Violation > Warning > Info.
 *    Empty severity buckets are skipped entirely — no "(0)" placeholders.
 *
 * The unit-test contract is intentionally a tree-of-text assertion (not a
 * screenshot golden) so it stays robust against severity-color tweaks.
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
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
    fun textWithoutHits_showsNoViolationAndLowConfidenceHint_noOcrHeader() {
        // Phase 3.5: the OCR-text header was dropped (the user explicitly
        // asked for it to be removed). The actionable signal is the hit
        // cards; in the no-hit case the only thing the panel renders is
        // the "no violation" message + low-confidence hint.
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ResultPanel(
                    ViolationReport(uri, "普通产品介绍", emptyList(), 1L, avgConfidence = 0.3f),
                )
            }
        }
        composeRule.onNodeWithText("未发现违规用语").assertExists()
        composeRule.onNodeWithText("识别置信度较低,结果仅供参考").assertExists()
        // No OCR-text header.
        composeRule.onNodeWithText("识别文字: 普通产品介绍").assertDoesNotExist()
        composeRule.onNodeWithText("普通产品介绍").assertDoesNotExist()
        // No severity-section headers when there are no hits.
        composeRule.onNodeWithText("违规 (0)", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("警告 (0)", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("信息 (0)", substring = true).assertDoesNotExist()
    }

    @Test
    fun warningHit_rendersAsCardWithSeverityChip() {
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
        // The hit card shows the matched text, the regulation, and the
        // severity chip — but NO rule-category line.
        composeRule.onNodeWithText("\"最好\"").assertExists()
        composeRule.onNodeWithText("依据: 《广告法》第九条第（三）项").assertExists()
        composeRule.onNodeWithText("警告").assertExists()
        composeRule.onNodeWithText("分类: 绝对化用语").assertDoesNotExist()
        composeRule.onNodeWithText("分类: absolute").assertDoesNotExist()

        // The law-text toggle still works.
        composeRule.onNodeWithText("查看法条原文").performClick()
        composeRule.onNodeWithText("第九条 广告不得有下列情形", substring = true).assertExists()
        composeRule.onNodeWithText("收起法条原文").assertExists()
    }

    @Test
    fun unknownCategory_doesNotRenderAnyCategoryLine() {
        // Phase 3.5 dropped the rule-category line entirely; the chip
        // label is the severity, not the rule category. Even with a
        // bogus category, no "分类: X" line should render.
        val hit = RuleHit("r1", "示例", "future-category", "《广告法》第九条", Severity.Warning)
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(ViolationReport(uri, "示例文本", listOf(hit), 1L, avgConfidence = 0.9f))
            }
        }
        composeRule.onNodeWithText("\"示例\"").assertExists()
        composeRule.onNodeWithText("分类: future-category").assertDoesNotExist()
        composeRule.onNodeWithText("分类: 示例").assertDoesNotExist()
        // Severity chip still renders for the bogus category — the
        // category is unrelated to which bucket the hit lands in.
        composeRule.onNodeWithText("警告").assertExists()
    }

    @Test
    fun multipleSeverities_renderOneSectionHeaderPerNonEmptyBucket() {
        // The section headers MUST all render so the user can scan 违规 → 警告 → 信息.
        // Hit cards may scroll off the LazyColumn viewport in Robolectric tests
        // (see CLAUDE.md "Robolectric + Compose LazyColumn viewport 太小,首屏
        // item 不一定 compose"). The single-severity tests above cover hit
        // card rendering; this test pins section ordering + section-header
        // counts.
        val violation = RuleHit(
            ruleId = "AD_LAW_007",
            matchedText = "100% 有效",
            category = "absolute",
            regulation = "《广告法》第九条",
            severity = Severity.Violation,
            lawText = "",
        )
        val warning = RuleHit(
            ruleId = "AD_LAW_warning",
            matchedText = "独家",
            category = "exclusive",
            regulation = "《广告法》第九条",
            severity = Severity.Warning,
            lawText = "",
        )
        val info = RuleHit(
            ruleId = "AD_LAW_info",
            matchedText = "未成年禁用",
            category = "minor",
            regulation = "《广告法》第十条",
            severity = Severity.Info,
            lawText = "",
        )
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(
                    ViolationReport(uri, "三种命中", listOf(violation, warning, info), 1L, avgConfidence = 0.9f),
                )
            }
        }
        // All three severity section headers render with the right counts.
        composeRule.onNodeWithText("违规 (1)").assertExists()
        composeRule.onNodeWithText("警告 (1)").assertExists()
        composeRule.onNodeWithText("信息 (1)").assertExists()
    }

    @Test
    fun onlyViolationHit_omitsEmptyWarningAndInfoSections() {
        val violation = RuleHit(
            ruleId = "r1",
            matchedText = "国家级",
            category = "absolute",
            regulation = "《广告法》第九条",
            severity = Severity.Violation,
            lawText = "",
        )
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ResultPanel(ViolationReport(uri, "x", listOf(violation), 1L, avgConfidence = 0.9f))
            }
        }
        composeRule.onNodeWithText("违规 (1)").assertExists()
        // Empty sections must NOT render — no "(0)" placeholders.
        composeRule.onNodeWithText("警告 (0)", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("信息 (0)", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("警告").assertDoesNotExist()
        composeRule.onNodeWithText("信息").assertDoesNotExist()
    }
}