package com.icespiritai.offline.glasses.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.icespiritai.offline.R
import com.icespiritai.offline.glasses.GlassesDevice
import com.icespiritai.offline.glasses.GlassesDeviceStore
import com.icespiritai.offline.glasses.GlassesPermissions
import com.icespiritai.offline.glasses.GlassesPhotoCaptureRepository
import com.icespiritai.offline.glasses.GlassesSystemIntents
import com.icespiritai.offline.glasses.GlassesTarget
import com.icespiritai.offline.glasses.resolveGlassesTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Compose overlay driving the smart-glasses capture pipeline.
 *
 * **Wiring assumption (v1).** The caller has already verified:
 *   - the glasses are paired in OS Bluetooth settings, and
 *   - `BLUETOOTH_CONNECT` is granted (see [GlassesPermissions]).
 *
 * The overlay still re-derives both, because a self-contained pre-flight is
 * what turns "we assumed the caller checked" into "the user gets a prompt".
 * When the target cannot be resolved it shows a [GlassesNoticeDialog]
 * instead of the capture dialog — see the v0.4.3 note below.
 *
 * Once a target is resolved the overlay auto-connects and auto-starts the
 * capture. On `Success`, [onCaptured] fires with the JPEG FileProvider URI
 * and the caller is expected to feed it into
 * [com.icespiritai.offline.IceSpiritVisionViewModel.startAnalysis].
 *
 * **v0.4.3 fix (2026-09-16 crash report).** Before this version the
 * unresolved-device branch did `repository.reset()` and rendered the
 * `Idle` arm — a dead-end dialog reading 「搜索附近的眼镜」 with no close
 * button; the resolver above it called `BluetoothAdapter.bondedDevices`
 * unguarded, which is a `SecurityException` on Android 12+ without
 * `BLUETOOTH_CONNECT`. Now:
 *
 *  - resolution goes through the shared pure resolver
 *    ([resolveGlassesTarget]) over a guard-safe snapshot;
 *  - every failure becomes a notice with the action that fixes it
 *    (去蓝牙设置配对 / 继续授权 / 去应用设置);
 *  - 重试 re-runs the *whole* session (resolve → connect → capture) rather
 *    than reconnecting blindly to `loadLastPaired()`, which used to
 *    `return@launch` silently when the store was empty.
 *
 * **v1 limitation.** Single-device flow. Multi-glasses picker is a v2
 * follow-up (see plan §"Multi-glasses note").
 *
 * **v1 limitation.** No in-overlay BLE *scan* — the user pairs in system
 * Settings first. Per the recommendation in the response to the user
 * clarifying "BLE 连接 ≠ 免配对", this is intentional for v1.
 */
@Composable
fun GlassesCaptureOverlay(
    repository: GlassesPhotoCaptureRepository,
    deviceStore: GlassesDeviceStore,
    scope: CoroutineScope,
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by repository.state.collectAsState()
    val context = LocalContext.current

    /**
     * Non-null while a pre-flight problem is blocking the session. Lives
     * outside the repository's state machine on purpose: the repository
     * describes *BLE session* state, and "the user never got as far as a
     * session" is not one of its stages.
     */
    var preflight by remember { mutableStateOf<GlassesNotice?>(null) }

    fun resolveTarget(): GlassesTarget = runCatching {
        resolveGlassesTarget(
            lastPairedAddress = deviceStore.loadLastPaired(),
            snapshot = GlassesDevice.bondedSnapshot(context),
            nowMs = System.currentTimeMillis(),
        )
    }.getOrElse { GlassesTarget.NotPaired }

    suspend fun runSession() {
        val target = resolveTarget()
        if (target !is GlassesTarget.Ready) {
            preflight = target.noticeOrNull() ?: GlassesNotice.NotPaired
            return
        }
        preflight = null
        // Refresh the persisted address so the next launch starts from the
        // device we are about to actually talk to.
        deviceStore.saveLastPaired(target.device.address)
        try {
            repository.ensureConnected(target.device)
            // Auto-start the capture once Ready.
            repository.capture()?.let(onCaptured)
        } catch (e: Throwable) {
            // Repository has already transitioned state to Failed; let the
            // UI render that.
        }
    }

    // The home screen gates on the permission before opening this overlay,
    // so this launcher is the belt-and-braces path for a caller that
    // doesn't (and for the notice's 「继续授权」 button).
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) scope.launch { runSession() }
    }

    LaunchedEffect(Unit) { runSession() }

    val notice = preflight
    if (notice != null) {
        GlassesNoticeDialog(
            notice = notice,
            onDismiss = onDismiss,
            onOpenBluetoothSettings = {
                GlassesSystemIntents.openBluetoothSettings(context)
            },
            onRequestPermission = {
                val missing = GlassesPermissions.missing(context)
                if (missing.isEmpty()) {
                    scope.launch { runSession() }
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            },
            onOpenAppSettings = {
                GlassesSystemIntents.openAppSettings(context)
            },
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                when (val s = state) {
                    is GlassesPhotoCaptureRepository.GlassesCaptureState.Idle -> {
                        Text(text = stringResource(R.string.glasses_scan_title))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.Connecting -> {
                        Text(text = stringResource(R.string.glasses_connecting, s.device.name))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.NegotiatingMtu -> {
                        Text(text = stringResource(R.string.glasses_negotiating_mtu))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.DiscoveringServices -> {
                        Text(text = stringResource(R.string.glasses_discovering_services))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.EnablingNotifies -> {
                        Text(text = stringResource(R.string.glasses_enabling_notifies))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.Ready -> {
                        Text(text = stringResource(R.string.glasses_ready))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.Capturing -> {
                        Text(
                            text = stringResource(
                                if (s.progress.stage ==
                                    GlassesPhotoCaptureRepository.CaptureProgress.Stage.Repairing
                                ) {
                                    R.string.glasses_capture_repairing
                                } else {
                                    R.string.glasses_capturing
                                },
                            ),
                        )
                        Spacer(Modifier.height(12.dp))
                        val total = s.progress.totalBytes ?: 0
                        if (total > 0) {
                            Text(
                                text = stringResource(
                                    R.string.glasses_capture_progress,
                                    s.progress.bytesReceived,
                                    total,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.Success -> {
                        Text(text = stringResource(R.string.glasses_capture_success, s.latencyMs.toInt()))
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.glasses_action_close))
                        }
                    }

                    is GlassesPhotoCaptureRepository.GlassesCaptureState.Failed -> {
                        Text(
                            text = stringResource(R.string.glasses_capture_failed, s.reason),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row {
                            if (s.retryable) {
                                Button(onClick = {
                                    scope.launch {
                                        repository.reset()
                                        // Re-resolve instead of trusting the
                                        // persisted address: the failure may
                                        // be "the remembered glasses is gone",
                                        // in which case the OS bonded list is
                                        // the only thing that knows better.
                                        runSession()
                                    }
                                }) {
                                    Text(stringResource(R.string.glasses_action_retry))
                                }
                                Spacer(Modifier.size(8.dp))
                            }
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.glasses_action_close))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Row helper (Compose Material3 doesn't ship a Row in this package).
@Composable
private fun Row(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
