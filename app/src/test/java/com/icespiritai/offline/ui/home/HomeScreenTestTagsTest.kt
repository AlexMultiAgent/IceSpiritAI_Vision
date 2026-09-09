package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression pin for the home-screen testTag wiring added in Opt-4
 * (2026-09-10). Tests assert each tag survives the Compose semantic
 * merge — if a future refactor removes the `.testTag(...)` modifier
 * or moves it onto a child that gets merged out (AnimatedContent /
 * TooltipBox have been known to swallow tags on their inner content),
 * the corresponding `assertExists()` fails immediately rather than
 * silently breaking downstream integration tests that rely on the
 * tag as a stable selector.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric
 * 4.13's maxSdk=34 (matches StatusBannerTest / CaptureBarTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTestTagsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusBanner_violation_exposesKpiTags() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                StatusBanner(
                    kind = StatusBannerKind.Violation,
                    violationCount = 1,
                    warningCount = 2,
                    infoCount = 3,
                )
            }
        }
        composeRule.onNodeWithTag(HomeScreenTestTags.STATUS_BANNER).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.KPI_VIOLATION).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.KPI_WARNING).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.KPI_INFO).assertExists()
    }

    @Test
    fun captureBar_noHits_exposesPickAndCaptureHidesExport() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = {},
                    onPick = {},
                    onExport = {},
                    hasHits = false,
                )
            }
        }
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_PICK).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_CAPTURE).assertExists()
        // hasHits = false hides the export slot — `assertDoesNotExist`
        // is the negative-side pin so a future regression that always
        // renders the export button (even disabled) trips this test.
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_EXPORT).assertDoesNotExist()
    }

    @Test
    fun captureBar_withHits_exposesAllThree() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = {},
                    onPick = {},
                    onExport = {},
                    hasHits = true,
                )
            }
        }
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_PICK).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_EXPORT).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_CAPTURE).assertExists()
    }

    @Test
    fun errorPanel_retryable_exposesRetryTag() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ErrorPanel(
                    code = ErrorCode.OCR_FAILED,
                    retryable = true,
                    onRetry = {},
                    onReset = {},
                )
            }
        }
        composeRule.onNodeWithTag(HomeScreenTestTags.ERROR_PANEL).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.ERROR_PANEL_RETRY).assertExists()
    }

    @Test
    fun errorPanel_nonRetryable_exposesBackTag() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                ErrorPanel(
                    code = ErrorCode.RULES_FAILED,
                    retryable = false,
                    onRetry = {},
                    onReset = {},
                )
            }
        }
        composeRule.onNodeWithTag(HomeScreenTestTags.ERROR_PANEL).assertExists()
        composeRule.onNodeWithTag(HomeScreenTestTags.ERROR_PANEL_BACK).assertExists()
    }
}