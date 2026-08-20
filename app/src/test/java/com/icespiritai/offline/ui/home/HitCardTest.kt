package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [HitCard] — the per-rule card rendered in the result
 * panel for each [RuleHit].
 *
 * Pins:
 *  - matchedText renders as the card title
 *  - the localized "分类: X" and "依据: X" lines render
 *  - the severity badge text is part of the card (Violation → "违规")
 *  - when `lawText` is blank, the 查看法条原文 button is hidden
 *  - when `lawText` is non-blank, the 查看法条原文 button toggles the
 *    lawText visibility on click
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HitCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleHit(
        lawText: String = "第九条 广告不得有下列情形...",
        severity: Severity = Severity.Violation,
    ) = RuleHit(
        ruleId = "AD_LAW_007",
        matchedText = "100% 有效",
        category = "绝对化用语",
        regulation = "《广告法》第 9 条",
        severity = severity,
        lawText = lawText,
    )

    @Test
    fun `renders matchedText, category, regulation, severity`() {
        val hit = sampleHit()
        composeRule.setContent {
            HitCard(hit = hit)
        }
        composeRule.onNodeWithText("100% 有效").assertExists()
        composeRule.onNodeWithText("分类: 绝对化用语").assertExists()
        composeRule.onNodeWithText("依据: 《广告法》第 9 条").assertExists()
        composeRule.onNodeWithText("违规").assertExists()
    }

    @Test
    fun `warning severity renders warning label`() {
        composeRule.setContent {
            HitCard(hit = sampleHit(severity = Severity.Warning))
        }
        composeRule.onNodeWithText("警告").assertExists()
    }

    @Test
    fun `info severity renders info label`() {
        composeRule.setContent {
            HitCard(hit = sampleHit(severity = Severity.Info))
        }
        composeRule.onNodeWithText("信息").assertExists()
    }

    @Test
    fun `lawText initially hidden behind show-law button`() {
        val law = "第九条 广告不得有下列情形..."
        composeRule.setContent {
            HitCard(hit = sampleHit(lawText = law))
        }
        composeRule.onNodeWithText("查看法条原文").assertExists()
        // Law text is gated behind the show-law click; should not be in
        // the tree yet.
        composeRule.onNodeWithText(law).assertDoesNotExist()
    }

    @Test
    fun `clicking show-law reveals the lawText`() {
        val law = "第九条 广告不得有下列情形..."
        composeRule.setContent {
            HitCard(hit = sampleHit(lawText = law))
        }
        composeRule.onNodeWithText("查看法条原文").performClick()
        composeRule.onNodeWithText(law).assertExists()
        // Toggling also flips the button label.
        composeRule.onNodeWithText("收起法条原文").assertExists()
    }

    @Test
    fun `clicking hide-law collapses the lawText again`() {
        val law = "第九条 广告不得有下列情形..."
        composeRule.setContent {
            HitCard(hit = sampleHit(lawText = law))
        }
        // Expand then collapse.
        composeRule.onNodeWithText("查看法条原文").performClick()
        composeRule.onNodeWithText(law).assertExists()
        composeRule.onNodeWithText("收起法条原文").performClick()
        composeRule.onNodeWithText(law).assertDoesNotExist()
    }

    @Test
    fun `blank lawText hides the show-law button entirely`() {
        composeRule.setContent {
            HitCard(hit = sampleHit(lawText = ""))
        }
        composeRule.onNodeWithText("100% 有效").assertExists()
        composeRule.onNodeWithText("查看法条原文").assertDoesNotExist()
        composeRule.onNodeWithText("收起法条原文").assertDoesNotExist()
    }
}