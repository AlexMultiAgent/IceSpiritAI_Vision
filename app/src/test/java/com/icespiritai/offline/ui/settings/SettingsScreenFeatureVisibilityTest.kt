package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI wiring smoke test for the 「功能可见性」card added in Task 6 of the
 * food-labeling feature plan.
 *
 * **Scope**: rendering-only. We verify the card is present, with the
 * title / description / both row labels, all scroll-reachable. The
 * underlying VM behaviour — `setFeatureVisible` accepting / rejecting
 * writes, emitting `SettingsSnackbar.LastFeatureCannotHide` /
 * `PersistFailed` — is covered exhaustively by `SettingsViewModelTest.kt`
 * (T2 landed these as 4 unit tests).
 *
 * **Why rendering-only and not a toggle test**: the production
 * `SettingsScreen` constructs its own `SettingsViewModel` internally via
 * `viewModel(factory = SettingsViewModel.factory(SettingsRepository(...)))`.
 * Exercising the toggle path would require either (a) injecting a fake
 * VM via a new production-side parameter (surface change for one test)
 * or (b) overriding the factory from the test (Robolectric-friendly but
 * fragile). Both options add production surface for a regression that is
 * already pinned by `SettingsViewModelTest`. The risk we're guarding
 * against — a string rename / accidental card drop / missing import —
 * is exactly what the three node-with-text assertions below catch.
 *
 * Mirrors `SettingsScreenTtsSectionTest` for handling the LazyColumn
 * viewport in Robolectric (the card sits below the fold; `performScrollTo`
 * is required before `assertIsDisplayed`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenFeatureVisibilityTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `feature visibility section shows title, description, and both switch labels`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = {}, currentEngineLabel = "跟随系统默认",
                    onOpenEnginePicker = {},
                )
            }
        }
        // FeatureVisibilitySection sits below the TTS card. Scroll to each
        // text node so it enters the Robolectric viewport before the
        // display assertion (LazyColumn-style framing; same pattern as
        // SettingsScreenTtsSectionTest).
        composeRule.onNodeWithText("功能可见性", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("勾选显示的功能,至少保留一个", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("广告招牌", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("食品标签", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }
}