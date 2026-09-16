package com.icespiritai.offline.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.AppGraph
import com.icespiritai.offline.glasses.ui.GlassesNoticeTestTags
import com.icespiritai.offline.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression tests for the 2026-09-16 crash report: tapping the home-screen
 * 「眼镜」 button with no glasses paired killed the app instead of telling
 * the user anything.
 *
 * Root cause (see `GlassesPermissions` / `GlassesDevice.bondedSnapshot`):
 * the click handler read `BluetoothAdapter.bondedDevices` on the main
 * thread without `BLUETOOTH_CONNECT` ever having been requested, so on
 * Android 12+ the first tap threw `SecurityException` out of the Compose
 * `onClick`.
 *
 * `sdk = 33` — the API level where the permission is enforced (the repo's
 * Robolectric setup caps at 34 and targetSdk is 37, so 33 is the closest
 * enforceable level).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenGlassesNoticeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val app = RuntimeEnvironment.getApplication()

    private fun enableGlassesToggle() {
        runBlocking { SettingsRepository(app).setGlassesCaptureEnabled(true) }
    }

    /**
     * The button is opt-in (Settings → 智能眼镜), so it only renders once the
     * persisted flag has been read out of DataStore.
     */
    private fun awaitGlassesButton() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(HomeScreenTestTags.CAPTURE_BAR_GLASSES)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun tappingGlassesWithNothingPaired_showsANoticeInsteadOfCrashing() {
        // The report's configuration: switch ON, nothing paired in system
        // Bluetooth, nothing remembered by the app.
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setEnabled(true)
        shadowOf(adapter).setBondedDevices(emptySet())
        AppGraph.glassesDeviceStore(app).clear()
        enableGlassesToggle()

        composeRule.setContent { HomeScreen(onOpenSettings = {}) }
        awaitGlassesButton()

        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_GLASSES).performClick()

        // Pre-fix this line was never reached: the process died inside the
        // click handler. Post-fix the user gets an actionable dialog.
        composeRule.onNodeWithTag(GlassesNoticeTestTags.DIALOG).assertExists()
        composeRule.onNodeWithText("未配对智能眼镜").assertExists()
    }

    @Test
    fun tappingGlassesWithoutBluetoothPermission_asksForItAndSurvives() {
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        AppGraph.glassesDeviceStore(app).clear()
        enableGlassesToggle()

        composeRule.setContent { HomeScreen(onOpenSettings = {}) }
        awaitGlassesButton()

        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_GLASSES).performClick()

        val request = shadowOf(composeRule.activity).lastRequestedPermission
        assertNotNull("the tap must request the runtime permission", request)
        assertTrue(
            "BLUETOOTH_CONNECT must be the permission asked for",
            request.requestedPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT),
        )
        // With no grant there is nothing to capture with, so no dialog and
        // no overlay — and, critically, no crash.
        composeRule.onNodeWithTag(GlassesNoticeTestTags.DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeScreenTestTags.CAPTURE_BAR_GLASSES).assertExists()
    }
}
