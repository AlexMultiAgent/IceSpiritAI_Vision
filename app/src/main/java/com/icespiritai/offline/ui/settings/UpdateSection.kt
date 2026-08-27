package com.icespiritai.offline.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.R
import com.icespiritai.offline.settings.SettingsViewModel
import com.icespiritai.offline.updater.UpdateState

@Composable
fun UpdateSection(
    viewModel: SettingsViewModel,
    onOpenUpdateDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // POST_NOTIFICATIONS runtime permission (Android 13+ / API 33+). The
    // FGS download pipeline already declares the permission in
    // AndroidManifest.xml and UpdateDownloadNotifier fires notifications on
    // progress / ready / failed, but on API 33+ the OS only shows FGS
    // notifications if the runtime permission is granted. Without this
    // request, the user downloads an APK blind — no progress bar, no "ready
    // to install" prompt in the notification tray.
    //
    // We prompt WHEN the UpdateAvailable card first appears (semantically
    // the user is about to kick off a download — the permission request
    // makes the most sense there). The download itself does NOT gate on the
    // result: if the user denies, the FGS still runs, only the notification
    // becomes invisible. (See AGP 9.3 + minSdk 26 build matrix: below 33
    // the permission is auto-granted so this branch is a no-op.)
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result intentionally ignored — see KDoc above */ }
    // Track whether we've already prompted for this UpdateAvailable so a
    // recomposition doesn't spam the system dialog. Resets when state
    // leaves UpdateAvailable (e.g. user cancelled).
    val promptedForNotif = remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is UpdateState.UpdateAvailable && !promptedForNotif.value) {
            promptedForNotif.value = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (state !is UpdateState.UpdateAvailable) {
            // Reset so the next time UpdateAvailable appears we re-prompt
            // (covers Failed → retry → UpdateAvailable path).
            promptedForNotif.value = false
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.update_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            UpdateState.Idle -> Button(onClick = { viewModel.refresh() }) {
                Text(stringResource(R.string.update_check_button))
            }
            UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.update_checking))
            }
            is UpdateState.UpToDate -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.update_up_to_date, currentVersionString()))
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text(stringResource(R.string.update_recheck_button))
                    }
                }
            }
            is UpdateState.UpdateAvailable -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.update_available_banner, s.info.versionName),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.download(s.info, context) }) {
                            Text(stringResource(R.string.update_download_button))
                        }
                        TextButton(onClick = onOpenUpdateDetail) {
                            Text(stringResource(R.string.update_detail_button))
                        }
                    }
                }
            }
            is UpdateState.Downloading -> {
                val totalMb = s.totalBytes / 1_000_000.0
                val doneMb = s.downloadedBytes / 1_000_000.0
                val progress = if (s.totalBytes > 0L) {
                    (s.downloadedBytes.toFloat() / s.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.update_downloading, doneMb, totalMb))
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { viewModel.cancel(context) }) {
                    Text(stringResource(R.string.update_cancel))
                }
            }
            is UpdateState.ReadyToInstall -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.update_ready_to_install))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.install(s.file, context) }) {
                            Text(stringResource(R.string.update_install_button))
                        }
                    }
                }
            }
            is UpdateState.Failed -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(failureLabel(s.result), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        // User explicitly cancelled — no retry prompt; tapping
                        // "Download" again from UpdateAvailable is the recovery.
                        val showRetry = s.result !is
                            com.icespiritai.offline.updater.UpdateCheckResult.Failed.DownloadInterrupted.Cancelled
                        if (showRetry) {
                            TextButton(onClick = {
                                viewModel.retry(
                                    context = context,
                                    jsonUrl = com.icespiritai.offline.BuildConfig.UPDATE_JSON_URL,
                                )
                            }) {
                                Text(stringResource(R.string.update_retry_button))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun failureLabel(result: com.icespiritai.offline.updater.UpdateCheckResult.Failed): String =
    when (result) {
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.NoNetwork ->
            stringResource(R.string.update_failed_no_network)
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.ServerError ->
            stringResource(R.string.update_failed_server, result.httpCode)
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.ParseError ->
            stringResource(R.string.update_failed_parse)
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.SignatureMismatch ->
            stringResource(R.string.update_failed_cert_mismatch)
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.DownloadInterrupted.Cancelled ->
            stringResource(R.string.update_failed_cancelled)
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable ->
            stringResource(R.string.update_failed_network_unreachable)
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.DownloadInterrupted.Other ->
            stringResource(R.string.update_failed_download)
    }

private fun currentVersionString(): String =
    com.icespiritai.offline.BuildConfig.VERSION_NAME