package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [StatusBanner] — the colored status strip above the
 * result panel. Color is branch-by-kind + branch-by-isDark, but Compose
 * UI tests can't read pixel data, so we exercise:
 *
 *  - the supplied `text` is rendered
 *  - all five [StatusBannerKind] branches render without crashing
 *  - dark + light theme paths exercise the `isDark` discriminator without
 *    throwing
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the supplied text`() {
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Idle, text = "等待扫描…")
        }
        composeRule.onNodeWithText("等待扫描…").assertExists()
    }

    @Test
    fun `Idle StatusBannerKind renders without crashing`() {
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Idle, text = "kind=Idle")
        }
        composeRule.onNodeWithText("kind=Idle").assertExists()
    }

    @Test
    fun `Loading StatusBannerKind renders without crashing`() {
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Loading, text = "kind=Loading")
        }
        composeRule.onNodeWithText("kind=Loading").assertExists()
    }

    @Test
    fun `Success StatusBannerKind renders without crashing`() {
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Success, text = "kind=Success")
        }
        composeRule.onNodeWithText("kind=Success").assertExists()
    }

    @Test
    fun `Warning StatusBannerKind renders without crashing`() {
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Warning, text = "kind=Warning")
        }
        composeRule.onNodeWithText("kind=Warning").assertExists()
    }

    @Test
    fun `Violation StatusBannerKind renders without crashing`() {
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Violation, text = "kind=Violation")
        }
        composeRule.onNodeWithText("kind=Violation").assertExists()
    }

    @Test
    fun `renders cleanly under dark color scheme`() {
        // Exercises the isDark branch — production reads
        // MaterialTheme.colorScheme.background to pick dark vs light
        // token bundles. Without a wrapped MaterialTheme the default
        // light scheme still works, but we want to lock that the dark
        // path doesn't throw on the color-copy operations.
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StatusBanner(kind = StatusBannerKind.Violation, text = "违规")
            }
        }
        composeRule.onNodeWithText("违规").assertExists()
    }

    @Test
    fun `renders with long text without crashing`() {
        // Sanity: a single-line banner with a long string should not
        // overflow the layout (Compose wraps in Box.fillMaxWidth, but
        // the production code passes `text = ...` directly to a Text
        // without a maxLines). We don't assert overflow behavior —
        // just that the composable doesn't throw.
        val long = "X".repeat(500)
        composeRule.setContent {
            StatusBanner(kind = StatusBannerKind.Warning, text = long)
        }
        composeRule.onNodeWithText(long).assertExists()
    }
}