package com.icespiritai.offline.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.R
import com.icespiritai.offline.settings.SettingsViewModel
import com.icespiritai.offline.updater.UpdateState

@Composable
fun UpdateSection(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                    Text(stringResource(R.string.update_up_to_date, currentVersionString(s.currentVersionCode)))
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
                        Spacer(Modifier.height(4.dp))
                        Text(s.info.changelog, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.download(s.info, context) }) {
                            Text(stringResource(R.string.update_download_button))
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
            }
            is UpdateState.ReadyToInstall -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.update_ready_to_install))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.install(s.file, context) }) {
                            Text(stringResource(R.string.update_download_button))
                        }
                    }
                }
            }
            is UpdateState.Failed -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(failureLabel(s.result), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.update_retry_button))
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
        is com.icespiritai.offline.updater.UpdateCheckResult.Failed.DownloadInterrupted ->
            stringResource(R.string.update_failed_download)
    }

private fun currentVersionString(versionCode: Int): String =
    com.icespiritai.offline.BuildConfig.VERSION_NAME