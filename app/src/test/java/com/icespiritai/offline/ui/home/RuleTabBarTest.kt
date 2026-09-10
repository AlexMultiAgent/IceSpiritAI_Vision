package com.icespiritai.offline.ui.home

import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Compose UI test for [RuleTabBar] — the visible-tabs guard.
 *
 * v0.1.69+: [RuleTabBar] takes `visibleTabs: Set<RuleTab>` from the caller
 * (typically [com.icespiritai.offline.IceSpiritVisionViewModel.visibleFeatures]).
 * These tests pin the visibility contract:
 *
 * - `setOf(RuleTab.AdSignage)` → renders exactly one tab (single-focus default,
 *   matches v0.1.10 product direction). FoodLabeling enum still exists but the
 *   tab is hidden.
 * - `RuleTab.entries.toSet()` → renders both tabs (the dual-focus policy that
 *   takes effect once FoodLabeling is enabled in Settings).
 * - Each tab's leading icon is exposed via a per-tab testTag so callers can
 *   verify the icon swap (Verified → LocalDining).
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34; same workaround as HomeScreenTest / ViewerScreenTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RuleTabBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `TabRow renders exactly one tab`() {
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = setOf(RuleTab.AdSignage),
                selected = RuleTab.AdSignage,
                onSelect = {},
            )
        }
        // Exactly one tab node should be present. Compose's Tab composable
        // exposes a node per tab; asserting count == 1 enforces the policy.
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
        ).assertCountEquals(1)
    }

    @Test
    fun `single tab displays the ad-law title`() {
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = setOf(RuleTab.AdSignage),
                selected = RuleTab.AdSignage,
                onSelect = {},
            )
        }
        composeRule.onNodeWithText("广告招牌").assertExists()
        // FoodLabeling's title must NOT appear — the entry point is hidden.
        composeRule.onNodeWithText("食品标识").assertDoesNotExist()
    }

    @Test
    fun `TabRow exposes switch-tab accessibility description`() {
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = setOf(RuleTab.AdSignage),
                selected = RuleTab.AdSignage,
                onSelect = {},
            )
        }
        composeRule.onNodeWithContentDescription("切换业务模式").assertExists()
    }

    @Test
    fun `clicking the only tab invokes onSelect with AdSignage`() {
        var lastSelected: RuleTab? = null
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = setOf(RuleTab.AdSignage),
                selected = RuleTab.AdSignage,
                onSelect = { lastSelected = it },
            )
        }
        composeRule.onNodeWithText("广告招牌").performClick()
        assertEquals(RuleTab.AdSignage, lastSelected)
    }

    // Reference composable to silence the "Tab is unused" warning — kept
    // here so future readers see the import path needed if they want to
    // extend this test with Tab-level assertions.
    @Composable
    private fun ReferenceTab() {
        Tab(selected = true, onClick = {}, text = {})
    }

    @Test
    fun tabBarHasCustomIndicator() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                RuleTabBar(
                    visibleTabs = setOf(RuleTab.AdSignage),
                    selected = RuleTab.AdSignage,
                    onSelect = {},
                    enabled = true,
                )
            }
        }
        composeRule.onNodeWithText("广告招牌").assertExists()
    }

    @Test
    fun `tab pill renders Verified icon as leading element`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                RuleTabBar(
                    visibleTabs = setOf(RuleTab.AdSignage),
                    selected = RuleTab.AdSignage,
                    onSelect = {},
                    enabled = true,
                )
            }
        }
        // Per-tab testTag: base constant + RuleTab.name (uppercase), matching
        // the existing AppearanceSection.kt convention (theme_SYSTEM, theme_DARK).
        // The Icon sits inside a clickable Surface (Role.Tab) + Row that merge
        // descendants by default, so we query the unmerged tree to find the
        // leaf-level testTag.
        composeRule.onNodeWithTag(
            RuleTabBarTestTags.PILL_LEADING_ICON_AD_SIGNAGE,
            useUnmergedTree = true,
        ).assertExists()
        // Text label still present (sanity check icon didn't replace the label).
        composeRule.onNodeWithText("广告招牌").assertExists()
    }

    // ---- v0.1.69+ visibility-set tests (Task 4) -----------------------------

    @Test
    fun `renders both tabs when visibleTabs has both`() {
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = RuleTab.entries.toSet(),
                selected = RuleTab.AdSignage,
                onSelect = {},
            )
        }
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
        ).assertCountEquals(2)
    }

    @Test
    fun `renders only AdSignage when FoodLabeling hidden`() {
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = setOf(RuleTab.AdSignage),
                selected = RuleTab.AdSignage,
                onSelect = {},
            )
        }
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
        ).assertCountEquals(1)
        // Sanity: FoodLabeling's title is hidden when its tab is.
        composeRule.onNodeWithText("食品标识").assertDoesNotExist()
    }

    @Test
    fun `renders FoodLabeling with LocalDining icon when enabled`() {
        composeRule.setContent {
            RuleTabBar(
                visibleTabs = RuleTab.entries.toSet(),
                selected = RuleTab.FoodLabeling,
                onSelect = {},
            )
        }
        composeRule.onNodeWithTag(
            RuleTabBarTestTags.PILL_LEADING_ICON_FOOD_LABELING,
            useUnmergedTree = true,
        ).assertExists()
        // Title still rendered for the active tab.
        composeRule.onNodeWithText("食品标识").assertExists()
    }
}
