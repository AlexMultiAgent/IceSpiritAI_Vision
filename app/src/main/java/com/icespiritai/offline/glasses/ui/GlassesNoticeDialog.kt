package com.icespiritai.offline.glasses.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.icespiritai.offline.R
import com.icespiritai.offline.glasses.GlassesTarget

/**
 * Pre-flight problems the user has to fix before a glasses capture can
 * start, as a value the UI can render.
 *
 * **Why a dialog and not a Toast** (v0.4.3): the previous "no glasses
 * paired" feedback was `Toast(R.string.settings_glasses_not_paired)`, whose
 * text is a bare noun phrase ("未配对智能眼镜") — no reason, no next step,
 * and on Android 12+ another app can replace it before the user reads it.
 * Every arm below is now a blocking dialog with the action that actually
 * resolves it.
 */
sealed interface GlassesNotice {

    /** Nothing paired / nothing remembered — the crash-report scenario. */
    data object NotPaired : GlassesNotice

    /** `BLUETOOTH_CONNECT` refused; the OS will still show the prompt. */
    data object PermissionDenied : GlassesNotice

    /** Refused twice (Android 11+) or "don't ask again" — only Settings helps. */
    data object PermissionDeniedForever : GlassesNotice

    /** Permission missing without a request having been made yet. */
    data object PermissionMissing : GlassesNotice

    /** Radio off. */
    data object BluetoothOff : GlassesNotice

    /** No Bluetooth radio on this device. */
    data object BluetoothUnavailable : GlassesNotice
}

/**
 * Map a resolver failure to its dialog, or `null` for
 * [GlassesTarget.Ready] (which needs no notice — it opens the capture
 * overlay instead).
 */
fun GlassesTarget.noticeOrNull(): GlassesNotice? = when (this) {
    is GlassesTarget.Ready -> null
    GlassesTarget.ConnectPermissionMissing -> GlassesNotice.PermissionMissing
    GlassesTarget.BluetoothUnavailable -> GlassesNotice.BluetoothUnavailable
    GlassesTarget.BluetoothOff -> GlassesNotice.BluetoothOff
    GlassesTarget.NotPaired -> GlassesNotice.NotPaired
}

/** Test tags for the notice dialog (main source, same pattern as `HomeScreenTestTags`). */
object GlassesNoticeTestTags {
    const val DIALOG = "glassesNotice_dialog"
    const val CONFIRM = "glassesNotice_confirm"
    const val DISMISS = "glassesNotice_dismiss"
}

/**
 * Blocking prompt for a glasses pre-flight problem.
 *
 * Callbacks are passed in rather than resolved internally so the same
 * dialog serves `HomeScreen` (which owns the permission launcher) and
 * `GlassesCaptureOverlay` (which owns the repository session). Each notice
 * picks the buttons that can actually fix it:
 *
 * | notice | primary action |
 * | --- | --- |
 * | [GlassesNotice.NotPaired] | 去蓝牙设置配对 |
 * | [GlassesNotice.BluetoothOff] | 去设置 (Bluetooth settings) |
 * | [GlassesNotice.PermissionMissing] / [GlassesNotice.PermissionDenied] | 继续授权 |
 * | [GlassesNotice.PermissionDeniedForever] | 去应用设置 |
 * | [GlassesNotice.BluetoothUnavailable] | (close only) |
 */
@Composable
fun GlassesNoticeDialog(
    notice: GlassesNotice,
    onDismiss: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String = when (notice) {
        GlassesNotice.NotPaired -> stringResource(R.string.settings_glasses_not_paired)
        GlassesNotice.BluetoothUnavailable -> stringResource(R.string.settings_glasses_title)
        else -> stringResource(R.string.glasses_notice_permission_title)
    }
    val body: String = when (notice) {
        GlassesNotice.NotPaired -> stringResource(R.string.glasses_not_paired_prompt)
        GlassesNotice.PermissionMissing -> stringResource(R.string.glasses_permission_denied)
        GlassesNotice.PermissionDenied -> stringResource(R.string.glasses_permission_denied_prompt)
        GlassesNotice.PermissionDeniedForever ->
            stringResource(R.string.glasses_permission_denied_forever_prompt)
        GlassesNotice.BluetoothOff -> stringResource(R.string.glasses_bluetooth_off)
        GlassesNotice.BluetoothUnavailable -> stringResource(R.string.glasses_no_adapter)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(GlassesNoticeTestTags.DIALOG),
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            when (notice) {
                GlassesNotice.BluetoothUnavailable -> Unit // nothing to launch
                GlassesNotice.NotPaired -> NoticeAction(
                    label = stringResource(R.string.settings_glasses_action_pair),
                    onClick = onOpenBluetoothSettings,
                )
                GlassesNotice.BluetoothOff -> NoticeAction(
                    label = stringResource(R.string.glasses_action_open_settings),
                    onClick = onOpenBluetoothSettings,
                )
                GlassesNotice.PermissionMissing, GlassesNotice.PermissionDenied -> NoticeAction(
                    label = stringResource(R.string.glasses_action_grant),
                    onClick = onRequestPermission,
                )
                GlassesNotice.PermissionDeniedForever -> NoticeAction(
                    label = stringResource(R.string.glasses_action_open_app_settings),
                    onClick = onOpenAppSettings,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(GlassesNoticeTestTags.DISMISS),
            ) {
                Text(stringResource(R.string.glasses_action_close))
            }
        },
    )
}

@Composable
private fun NoticeAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(GlassesNoticeTestTags.CONFIRM),
    ) {
        Text(label)
    }
}
