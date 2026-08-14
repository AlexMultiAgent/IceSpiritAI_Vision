package com.icespiritai.offline.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity

/**
 * Phase 1 Compose UI. Observes [IceSpiritVisionViewModel.state] and renders the
 * current [AnalysisState] branch. Text-only summary on `Complete` — image
 * preview (Coil) is deferred to a later task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: IceSpiritVisionViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::startAnalysis) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result handled below; UI re-reads state */ }

    fun ensurePermissionThenPick() {
        val needsCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED
        if (needsCamera) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::ensurePermissionThenPick) {
                    Text(stringResource(R.string.action_pick_image))
                }
                OutlinedButton(onClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text(stringResource(R.string.action_take_photo))
                }
            }

            when (val s = state) {
                AnalysisState.Idle -> Text(stringResource(R.string.status_idle))

                is AnalysisState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    val labelRes = when (s.stage) {
                        AnalysisState.Loading.Stage.OcrRunning -> R.string.status_ocr_running
                        AnalysisState.Loading.Stage.RuleScanning -> R.string.status_rule_scanning
                    }
                    Text(stringResource(labelRes))
                }

                is AnalysisState.OcrDone -> {
                    if (s.lowConfidence) {
                        Text(
                            stringResource(R.string.status_low_confidence),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("OCR: ${s.text}", style = MaterialTheme.typography.bodySmall)
                }

                is AnalysisState.RuleScanned -> Text(
                    text = context.getString(R.string.status_violations_count, s.hits.size),
                    style = MaterialTheme.typography.bodyMedium,
                )

                is AnalysisState.Complete -> {
                    // No previewUri — text-only summary for Phase 1.
                    Text(
                        text = "OCR 文本: ${s.report.ocrText}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (s.report.hits.isEmpty()) {
                        Text(stringResource(R.string.status_no_violation))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.report.hits) { hit -> HitCard(hit) }
                        }
                    }
                }

                is AnalysisState.Error -> {
                    val msgRes = when (s.errorCode) {
                        ErrorCode.OCR_UNAVAILABLE -> R.string.error_ocr_unavailable
                        ErrorCode.OCR_FAILED -> R.string.error_ocr_failed
                        ErrorCode.RULES_FAILED -> R.string.error_rules_failed
                        ErrorCode.UNKNOWN -> R.string.error_unknown
                    }
                    Text(stringResource(msgRes), color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.retryable) {
                            Button(onClick = { viewModel.reset() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }) {
                            Text(stringResource(R.string.action_grant_permission))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: RuleHit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(hit.matchedText, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.hit_card_category, hit.category),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.hit_card_regulation, hit.regulation),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "严重等级: ${hit.severity.displayName()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun Severity.displayName(): String = when (this) {
    Severity.Info -> "信息"
    Severity.Warning -> "警告"
    Severity.Violation -> "违规"
}
