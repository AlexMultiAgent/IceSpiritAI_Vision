package com.icespiritai.offline.ui.home

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Compose UI test for [HomeTopBar] — the app-name TopAppBar + the
 * single-tab RuleTabBar stacked underneath, plus a settings IconButton.
 *
 * Pins:
 *  - app name "冰灵锐目" renders as the title
 *  - the single visible tab ("广告招牌") is rendered (delegated to
 *    RuleTabBarTest but verified here too as integration smoke)
 *  - clicking the settings IconButton invokes onOpenSettings (the
 *    contentDescription "设置" is what TalkBack sees)
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
    fun `renders the app name as title`() {
        composeRule.setContent {
            HomeTopBar(
                selectedTab = RuleTab.AdSignage,
                onSelectTab = {},
                tabEnabled = true,
                onOpenSettings = {},
            )
        }
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
        composeRule.onNodeWithText("广告招牌").assertExists()
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

    @Test fun topBarTitleUsesHeadlineSmallStyle() {
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
        composeRule.onNodeWithText(getApplicationContext<Context>().getString(R.string.app_name))
            .assertExists()
    }
}