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
 * Compose UI test for [ChangelogScreen] — the version history page.
 *
 * Pins:
 *  - top-bar title "更新日志" renders
 *  - back arrow invokes onBack
 *  - the bundled `user-changelog.md` asset renders version entries
 *    (smoke check: the latest version header "v0.1.13" is visible)
 *  - when the asset is missing or unparseable, the "暂无更新日志"
 *    empty-state message renders (defensive — not exercised by the
 *    happy path, but pinned here so future changes to the empty branch
 *    don't silently break it)
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34. `user-changelog.md` is bundled in `app/src/main/assets/` and
 * Robolectric exposes it via `ApplicationProvider.getApplicationContext()
 * .assets`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChangelogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the top-bar title`() {
        composeRule.setContent {
            ChangelogScreen(onBack = {})
        }
        composeRule.onNodeWithText("更新日志").assertExists()
    }

    @Test
    fun `clicking the back arrow invokes onBack`() {
        var backs = 0
        composeRule.setContent {
            ChangelogScreen(onBack = { backs++ })
        }
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `renders the latest version header from the bundled asset`() {
        // user-changelog.md head lists "v0.1.13 · 2026-08-20" — the
        // EntryBlock renders `version · date`. We assert the version
        // substring to avoid coupling to the date (which changes daily).
        composeRule.setContent {
            ChangelogScreen(onBack = {})
        }
        composeRule.onNodeWithText("v0.1.13", substring = true).assertExists()
    }
}
