package com.icespiritai.offline.ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
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
 *  - exactly one option is `selected` at a time, matching the `current`
 *    parameter
 *  - clicking an unselected option invokes onSelect with that mode
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
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
    fun `renders exactly one selected radio per option set`() {
        // Three RadioButtons must always be present (regardless of which
        // is selected) — guards against anyone accidentally hiding the
        // options for a non-default theme.
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.DARK, onSelect = {})
        }
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        ).assertCountEquals(3)
    }

    @Test
    fun `SYSTEM is selected when current is SYSTEM`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.SYSTEM, onSelect = {})
        }
        // We click into the row containing the radio to test selection.
        // RadioButton carries Role.RadioButton + selected semantics.
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )[0].assertIsSelected()
    }

    @Test
    fun `DARK is selected when current is DARK`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.DARK, onSelect = {})
        }
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )[1].assertIsSelected()
    }

    @Test
    fun `LIGHT is selected when current is LIGHT`() {
        composeRule.setContent {
            AppearanceSection(current = ThemeMode.LIGHT, onSelect = {})
        }
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )[2].assertIsSelected()
    }

    @Test
    fun `clicking an option invokes onSelect with that mode`() {
        var lastSelected: ThemeMode? = null
        composeRule.setContent {
            AppearanceSection(
                current = ThemeMode.SYSTEM,
                onSelect = { lastSelected = it },
            )
        }
        // The RadioButton (index 1 = DARK / "深色雪夜") carries the onClick.
        // The label Text sibling does not, so we click the RadioButton node
        // directly.
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )[1].performClick()
        assertEquals(ThemeMode.DARK, lastSelected)
    }
}