package com.icespiritai.offline.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkOnWarning
import com.icespiritai.offline.ui.theme.DarkWarning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeverityBadgeTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `Violation shows 违规 label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DarkError)) {
                SeverityBadge(severity = Severity.Violation)
            }
        }
        composeRule.onNodeWithText("违规").assertExists()
    }

    @Test
    fun `Warning shows 警告 label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DarkWarning, onPrimary = DarkOnWarning)) {
                SeverityBadge(severity = Severity.Warning)
            }
        }
        composeRule.onNodeWithText("警告").assertExists()
    }

    @Test
    fun `Info shows 信息 label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DarkWarning, onPrimary = DarkOnWarning)) {
                SeverityBadge(severity = Severity.Info)
            }
        }
        composeRule.onNodeWithText("信息").assertExists()
    }
}
