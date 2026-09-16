package com.icespiritai.offline.glasses.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for [GlassesNoticeDialog] — the replacement for the
 * bare-noun Toast that used to be the only feedback for "no glasses
 * paired" (v0.4.3, 2026-09-16 crash report).
 *
 * The assertions pin the *user-visible* wording (`strings.xml`), because
 * the whole point of the change is that a noun phrase with no next step is
 * not an acceptable answer to a tap that cannot proceed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassesNoticeDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissals = 0
    private var bluetoothSettingsOpens = 0
    private var permissionRequests = 0
    private var appSettingsOpens = 0

    private fun show(notice: GlassesNotice) {
        composeRule.setContent {
            GlassesNoticeDialog(
                notice = notice,
                onDismiss = { dismissals++ },
                onOpenBluetoothSettings = { bluetoothSettingsOpens++ },
                onRequestPermission = { permissionRequests++ },
                onOpenAppSettings = { appSettingsOpens++ },
            )
        }
    }

    @Test
    fun notPaired_explainsAndOffersBluetoothSettings() {
        show(GlassesNotice.NotPaired)

        composeRule.onNodeWithTag(GlassesNoticeTestTags.DIALOG).assertExists()
        composeRule.onNodeWithText("未检测到已配对的智能眼镜。请先在系统蓝牙设置中配对眼镜,再回到首页点击「眼镜」拍照。")
            .assertExists()
        composeRule.onNodeWithText("去蓝牙设置配对").performClick()

        assertEquals(1, bluetoothSettingsOpens)
        assertEquals(0, permissionRequests)
    }

    @Test
    fun permissionDenied_offersAnotherRequest() {
        show(GlassesNotice.PermissionDenied)

        composeRule.onNodeWithText("继续授权").performClick()

        assertEquals(1, permissionRequests)
        assertEquals(0, appSettingsOpens)
    }

    @Test
    fun permissionDeniedForever_routesToAppSettings() {
        show(GlassesNotice.PermissionDeniedForever)

        composeRule.onNodeWithText("去应用设置").performClick()

        assertEquals(1, appSettingsOpens)
        assertEquals(0, permissionRequests)
    }

    @Test
    fun permissionMissing_namesThePermissionAndOffersTheRequest() {
        show(GlassesNotice.PermissionMissing)

        composeRule.onNodeWithText("需要蓝牙权限").assertExists()
        composeRule.onNodeWithText("继续授权").performClick()

        assertEquals(1, permissionRequests)
    }

    @Test
    fun bluetoothOff_pointsAtSystemSettings() {
        show(GlassesNotice.BluetoothOff)

        composeRule.onNodeWithText("请打开系统蓝牙").assertExists()
        composeRule.onNodeWithText("去设置").performClick()

        assertEquals(1, bluetoothSettingsOpens)
    }

    @Test
    fun bluetoothUnavailable_hasNothingToLaunch() {
        show(GlassesNotice.BluetoothUnavailable)

        composeRule.onNodeWithText("设备不支持蓝牙").assertExists()
        // No action can fix a missing radio — only 关闭.
        composeRule.onNodeWithTag(GlassesNoticeTestTags.CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithText("关闭").performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun dismissButton_closesWithoutSideEffects() {
        show(GlassesNotice.NotPaired)

        composeRule.onNodeWithTag(GlassesNoticeTestTags.DISMISS).performClick()

        assertEquals(1, dismissals)
        assertEquals(0, bluetoothSettingsOpens)
    }
}
