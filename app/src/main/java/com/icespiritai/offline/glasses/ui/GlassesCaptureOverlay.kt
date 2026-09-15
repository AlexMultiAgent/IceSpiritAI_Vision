package com.icespiritai.offline.glasses.ui

import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.icespiritai.offline.R
import com.icespiritai.offline.glasses.GlassesPhotoCaptureRepository
import kotlinx.coroutines.launch

/**
 * Minimal Compose overlay driving the smart-glasses capture pipeline.
 *
 * **Wiring assumption.** The caller has already verified:
 *   - the glasses are paired in OS Bluetooth settings
 *   - the user has granted `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` runtime permissions
 *
 * On first composition the overlay triggers an auto-reconnect to the
 * last-paired device (read via [GlassesDeviceStore]). Once connected,
 * it auto-starts the capture. On `Success`, [onCaptured] fires with the
 * JPEG FileProvider URI and the caller is expected to feed it into
 * [com.icespiritai.offline.IceSpiritVisionViewModel.startAnalysis].
 *
 * **v1 limitation.** Single-device flow. Multi-glasses picker is a v2
 * follow-up (see plan §"Multi-glasses note").
 *
 * **v1 limitation.** No in-overlay BLE scan — the user must pair in
 * system Settings first. Per the recommendation in the response to the
 * user clarifying "BLE 连接 ≠ 免配对", this is intentional for v1.
 */
@Composable
fun GlassesCaptureOverlay(
    repository: GlassesPhotoCaptureRepository,
    deviceStore: com.icespiritai.offline.glasses.GlassesDeviceStore,
    scope: kotlinx.coroutines.CoroutineScope,
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by repository.state.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // Resolve which device to talk to. Order of preference (smoke 21
        // 2026-09-15: the persisted `lastPaired` address is unreliable —
        // the user can swap glasses, the OS pairing record can be
        // deleted out from under us, and `GlassesDeviceStore` only
        // updates on a successful `onConnectionStateChange`, never on
        // pair churn. Falling back to `findBondedDevice(context)` is the
        // single source of truth: the OS BondedDevices list reflects
        // the *current* paired device, which is what BLE secure-connect
        // will actually authenticate against).
        val bonded = com.icespiritai.offline.glasses.GlassesDevice
            .findBondedDevice(context)
        val resolvedDevice = bonded
            ?: deviceStore.loadLastPaired()?.let { lastPaired ->
                com.icespiritai.offline.glasses.GlassesDevice(
                    address = lastPaired,
                    name = com.icespiritai.offline.glasses.GlassesDevice.NAME_PREFIX,
                    lastSeenMs = System.currentTimeMillis(),
                )
            }
        if (resolvedDevice == null) {
            repository.reset()
            return@LaunchedEffect
        }
        // Refresh the persisted address so the *next* launch (when the
        // OS list is in flux again) has a current value.
        deviceStore.saveLastPaired(resolvedDevice.address)
        try {
            repository.ensureConnected(resolvedDevice)
            // Auto-start the capture once Ready.
            val captured = repository.capture()
            if (captured != null) onCaptured(captured)
        } catch (e: Throwable) {
            // Repository has already transitioned state to Failed; let
            // the UI render that.
        }
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
                        Text(text = stringResource(R.string.glasses_capturing))
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
                                        val lastPaired = deviceStore.loadLastPaired() ?: return@launch
                                        val device = com.icespiritai.offline.glasses.GlassesDevice(
                                            address = lastPaired,
                                            name = com.icespiritai.offline.glasses.GlassesDevice.NAME_PREFIX,
                                            lastSeenMs = System.currentTimeMillis(),
                                        )
                                        repository.reset()
                                        repository.ensureConnected(device)
                                        val captured = repository.capture()
                                        if (captured != null) onCaptured(captured)
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
