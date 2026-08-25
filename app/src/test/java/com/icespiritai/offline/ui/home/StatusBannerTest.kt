package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [StatusBanner] — Phase 3.2 4-segment KPI horizontal bar.
 *
 * Pins:
 *  - Idle branch renders the empty hint (camera icon + "请对正图片后点击拍照")
 *  - Violation branch shows KPI numbers and severity labels ("违规", "警告", "信息")
 *  - Zero counts render correctly under the Success (light) theme
 *  - Loading branch shows the OCR/Rule phase text
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
    fun idleKpiRendersEmptyHint() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                StatusBanner(kind = StatusBannerKind.Idle)
            }
        }
        composeRule.onNodeWithText("请对正图片后点击拍照").assertExists()
    }

    @Test
    fun violationKpiRendersViolationCount() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                StatusBanner(
                    kind = StatusBannerKind.Violation,
                    violationCount = 3,
                    warningCount = 1,
                    infoCount = 0,
                )
            }
        }
        composeRule.onNodeWithText("3").assertExists()
        composeRule.onNodeWithText("1").assertExists()
        composeRule.onNodeWithText("违规").assertExists()
        composeRule.onNodeWithText("警告").assertExists()
    }

    @Test
    fun emptyCountsKpiRendersZeroForEach() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.LIGHT) {
                StatusBanner(
                    kind = StatusBannerKind.Success,
                    violationCount = 0,
                    warningCount = 0,
                    infoCount = 0,
                )
            }
        }
        composeRule.onAllNodesWithText("0").assertCountEquals(3)  // one zero per KPI cell (违规/警告/信息)
    }

    @Test
    fun loadingKpiRendersLoadingHint() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                StatusBanner(kind = StatusBannerKind.Loading, stage = StatusBannerStage.LoadingOcr)
            }
        }
        composeRule.onNodeWithText("OCR 识别中…").assertExists()
    }
}
