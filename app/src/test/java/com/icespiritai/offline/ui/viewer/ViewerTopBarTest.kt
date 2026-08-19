package com.icespiritai.offline.ui.viewer

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val title: String
        get() = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.viewer_title)

    @Test
    fun `ViewerTopBar shows viewer_title and back button`() {
        var backClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                ViewerTopBar(onBack = { backClicks++ })
            }
        }

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun `ViewerTopBar back button invokes onBack`() {
        var backClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                ViewerTopBar(onBack = { backClicks++ })
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }
}