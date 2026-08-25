package com.icespiritai.offline.ui.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [HomeTopBar] — the centered "冰灵⚡锐目" title +
 * the single-tab RuleTabBar underneath, plus a settings IconButton
 * pinned to the right.
 *
 * Pins:
 *  - The title is rendered as three Compose Text composables
 *    (prefix "冰灵" + bolt "⚡" + suffix "锐目") with `mergeDescendants`
 *    so TalkBack hears a single "冰灵锐目" contentDescription. This
 *    test asserts both the merged a11y node and the visible parts.
 *  - The single visible tab ("广告招牌") renders, "食品标识" is hidden.
 *  - Clicking the settings IconButton (TalkBack contentDescription
 *    "设置") invokes onOpenSettings.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the centered title parts with merged a11y`() {
        composeRule.setContent {
            HomeTopBar(
                selectedTab = RuleTab.AdSignage,
                onSelectTab = {},
                tabEnabled = true,
                onOpenSettings = {},
            )
        }
        // Each part is a visible Compose Text.
        composeRule.onNodeWithText("冰灵").assertIsDisplayed()
        composeRule.onNodeWithText("⚡").assertIsDisplayed()
        composeRule.onNodeWithText("锐目").assertIsDisplayed()
        // The mergeDescendants semantics expose "冰灵锐目" as a single
        // TalkBack node. onNodeWithText would also match individual
        // texts, so we use the explicit contentDescription matcher.
        val ctx = getApplicationContext<Context>()
        val title = ctx.getString(R.string.app_name)
        val nodes = composeRule.onAllNodesWithContentDescription(title)
        assertEquals(
            "merged title accessibility node must exist exactly once",
            1,
            nodes.fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `renders the single visible tab`() {
        composeRule.setContent {
            HomeTopBar(
                selectedTab = RuleTab.AdSignage,
                onSelectTab = {},
                tabEnabled = true,
                onOpenSettings = {},
            )
        }
        composeRule.onNodeWithText("广告招牌").assertIsDisplayed()
        composeRule.onNodeWithText("食品标识").assertDoesNotExist()
    }

    @Test
    fun `clicking the settings button invokes onOpenSettings`() {
        var opened = 0
        composeRule.setContent {
            HomeTopBar(
                selectedTab = RuleTab.AdSignage,
                onSelectTab = {},
                tabEnabled = true,
                onOpenSettings = { opened++ },
            )
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `bolt composable is present inside the themed top bar`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage,
                    onSelectTab = {},
                    tabEnabled = true,
                    onOpenSettings = {},
                )
            }
        }
        // The bolt is its own Text composable; verifying its presence
        // (and that it's distinct from the prefix/suffix) is enough of
        // a pin for this test layer. Visual color verification lives in
        // the screenshot test pipeline, not here.
        composeRule.onNodeWithText("⚡").assertIsDisplayed()
    }
}