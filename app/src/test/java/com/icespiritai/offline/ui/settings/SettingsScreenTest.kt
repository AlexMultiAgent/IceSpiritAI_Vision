package com.icespiritai.offline.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [SettingsScreen] — the top-level settings page.
 *
 * Pins:
 *  - top-bar title "设置" is rendered
 *  - back arrow ("返回" contentDescription) invokes onBack
 *  - Appearance section title "外观" is rendered (delegated to
 *    AppearanceSectionTest, smoke-verified here)
 *  - Update section title "更新" is rendered (delegated to
 *    UpdateSectionTest, smoke-verified here)
 *  - Changelog row "查看更新日志" + hint renders
 *  - Tapping the Changelog row invokes onOpenChangelog
 *  - Version footer is rendered (string contains the version label)
 *
 * The screen wires its own SettingsViewModel via `viewModel(factory = ...)`
 * + `SettingsRepository(context.applicationContext)`. Under Robolectric
 * the SharedPreferences-backed repository is functional, so we don't need
 * to inject a fake.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the top-bar title`() {
        composeRule.setContent {
            SettingsScreen(onBack = {}, onOpenChangelog = {})
        }
        composeRule.onNodeWithText("设置").assertExists()
    }

    @Test
    fun `clicking the back arrow invokes onBack`() {
        var backs = 0
        composeRule.setContent {
            SettingsScreen(onBack = { backs++ }, onOpenChangelog = {})
        }
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `renders the Appearance section`() {
        composeRule.setContent {
            SettingsScreen(onBack = {}, onOpenChangelog = {})
        }
        composeRule.onNodeWithText("外观").assertExists()
    }

    @Test
    fun `renders the Update section`() {
        composeRule.setContent {
            SettingsScreen(onBack = {}, onOpenChangelog = {})
        }
        composeRule.onNodeWithText("更新").assertExists()
    }

    @Test
    fun `renders the Changelog row label and hint`() {
        composeRule.setContent {
            SettingsScreen(onBack = {}, onOpenChangelog = {})
        }
        composeRule.onNodeWithText("查看更新日志").assertExists()
        composeRule.onNodeWithText("查看每个版本的修改变动").assertExists()
    }

    @Test
    fun `clicking the Changelog row invokes onOpenChangelog`() {
        var opens = 0
        composeRule.setContent {
            SettingsScreen(onBack = {}, onOpenChangelog = { opens++ })
        }
        composeRule.onNodeWithText("查看更新日志").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun `renders the version footer`() {
        composeRule.setContent {
            SettingsScreen(onBack = {}, onOpenChangelog = {})
        }
        // string is "版本: %1$s" — the prefix "版本:" must appear.
        composeRule.onNodeWithText("版本:", substring = true).assertExists()
    }
}
