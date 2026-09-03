package com.icespiritai.offline.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 *  - the bundled `user-changelog.md` asset's first section (rendered as
 *    the topmost EntryBlock) carries the current shipping version name,
 *    so the build pipeline stays aligned with the release branch
 *
 * Note on the version assertion: ChangelogScreen uses LazyColumn, which
 * only composes viewport-visible entries. Under Robolectric's default
 * test Activity metrics the viewport is too small for LazyColumn to
 * eagerly compose the first entry, so the original UI-level
 * `onNodeWithText("v0.1.X", substring=true)` assertion flakes. Asserting
 * on the parsed asset directly (JVM, no Compose) is the durable pin:
 * `VersionHistoryRenderer.parse(bundledAsset).first().version` MUST equal
 * the new versionCode's label after every bump, otherwise the in-app
 * banner and the standalone changelog screen diverge.
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
    fun `bundled asset first section matches the shipping version`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val md = ctx.assets.open("user-changelog.md").bufferedReader().use { it.readText() }
        val entries = VersionHistoryRenderer.parse(md)
        assertTrue(
            "bundled user-changelog.md must list the shipping version as its first " +
                "section (got first version=${entries.firstOrNull()?.version})",
        entries.firstOrNull()?.version == "v0.1.53",
        )
    }
}
