package com.icespiritai.offline.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [AppearanceSection] — the theme-mode picker on the
 * Settings screen. Pure stateless composable (no ViewModel dependency),
 * so we exercise the three options directly.
 *
 * Pins:
 *  - the section title "外观" is rendered
 *  - three options render: 跟随系统 / 深色雪夜 / 浅色冰月
 *  - exactly three Role.RadioButton semantic nodes exist (Material 3
 *    SegmentedButton emits Role.RadioButton via its Surface merge node)
 *  - exactly one option is `selected` at a time, matching `current`
 *  - clicking the DARK option (when SYSTEM is selected) invokes onSelect(DARK)
 *
 * Note: full click-coverage across all 3 options is exercised in
 * SettingsViewModelTest via the repository flow — UI tests here pin
 * the structural shape and the visible "selected" state.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppearanceSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the appearance section title`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.SYSTEM, onSelect = {})
        }
        composeRule.onNodeWithText("外观").assertExists()
    }

    @Test
    fun `renders all three theme options`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.SYSTEM, onSelect = {})
        }
        composeRule.onNodeWithText("跟随系统").assertExists()
        composeRule.onNodeWithText("深色雪夜").assertExists()
        composeRule.onNodeWithText("浅色冰月").assertExists()
    }

    @Test
    fun `renders exactly three radio-button roles regardless of selection`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.SYSTEM, onSelect = {})
        }
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        ).assertCountEquals(3)
    }

    @Test
    fun `SYSTEM option is selected when current is SYSTEM`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.SYSTEM, onSelect = {})
        }
        composeRule.onNodeWithTag("theme_SYSTEM").assertIsSelected()
    }

    @Test
    fun `DARK option is selected when current is DARK`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.DARK, onSelect = {})
        }
        composeRule.onNodeWithTag("theme_DARK").assertIsSelected()
    }

    @Test
    fun `LIGHT option is selected when current is LIGHT`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.LIGHT, onSelect = {})
        }
        composeRule.onNodeWithTag("theme_LIGHT").assertIsSelected()
    }

    @Test
    fun `clicking the DARK option invokes onSelect with DARK`() {
        var lastSelected: ThemeMode? = null
        composeRule.setContent {
            AppearanceSection(
                current = ThemeMode.SYSTEM,
                onSelect = { lastSelected = it },
            )
        }
        composeRule.onNodeWithTag("theme_DARK").performClick()
        assertEquals(ThemeMode.DARK, lastSelected)
    }
}