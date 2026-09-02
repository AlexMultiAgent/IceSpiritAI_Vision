package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [HomeTopBar] — the centered "冰灵锐目" title
 * (single Text reading app_name, v0.1.48+) and the single-tab RuleTabBar
 * underneath, plus a settings IconButton pinned to the right.
 *
 * Pins:
 *  - The title is a single Compose [androidx.compose.material3.Text]
 *    composable reading app_name ("冰灵锐目"); TalkBack reads it via
 *    Text's natural contentDescription (no mergeDescendants wrapper).
 *    The bolt ⚡ was removed in v0.1.48 per user feedback ("突兀"),
 *    collapsing the previous 3-Text layout (prefix + bolt + suffix)
 *    into this single Text.
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
    fun `home top bar renders the app name as a single centered Text`() {
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
        // Single Text reading app_name. Use onAllNodesWithText +
        // assertCountEquals(1) so the settings IconButton's separate
        // contentDescription ("设置") doesn't interfere — and so any
        // accidental duplicate render of the title fails loud.
        composeRule.onAllNodesWithText("冰灵锐目").assertCountEquals(1)
    }

    @Test
    fun `home top bar title reads app_name for TalkBack`() {
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
        // In Compose, a Text composable's `text` IS what TalkBack reads —
        // the `text` semantics property is the natural a11y surface for
        // Text nodes, not `contentDescription` (which is only set when
        // you opt in via `.semantics { contentDescription = ... }`). The
        // previous v0.1.47 3-Text layout (prefix + bolt + suffix) needed
        // a `mergeDescendants` wrapper to expose a single
        // contentDescription; v0.1.48's single-Text layout doesn't need
        // any wrapping — `onNodeWithText` matches what TalkBack hears.
        composeRule.onNodeWithText("冰灵锐目").assertExists()
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
}
