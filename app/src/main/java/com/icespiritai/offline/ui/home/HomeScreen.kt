package com.icespiritai.offline.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.export.ExportAction

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val viewModel: IceSpiritVisionViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(RuleTab.AdLaw) }
    // pendingUri persists across Loading→Complete so the image stays visible
    // before the analyzer finishes (AnalysisState.Loading has no URI field).
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            viewModel.startAnalysis(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* denied — UI shows banner below */ }

    fun ensurePermissionThenLaunchCamera() {
        val needsCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) != PackageManager.PERMISSION_GRANTED
        if (needsCamera) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // Phase 1 stub: PickVisualMedia instead of TakePicture (no extra
        // Activity result handling). Real camera capture is a follow-up.
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun pickFromGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun reset() {
        pendingUri = null
        viewModel.reset()
    }

    // Derive display state from AnalysisState
    val completeReport = (state as? AnalysisState.Complete)?.report
    val ocrResult = (state as? AnalysisState.OcrDone)
    val lineBoxes = ocrResult?.lineBoxes ?: emptyList()
    val hits = completeReport?.hits ?: emptyList()
    val showLineBoxes = (state is AnalysisState.OcrDone) || completeReport != null

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            selectedTab = selectedTab,
            onSelectTab = { tab ->
                if (tab == RuleTab.FoodLabel) {
                    Toast.makeText(context, R.string.tab_disabled_toast, Toast.LENGTH_SHORT).show()
                } else {
                    selectedTab = tab
                    reset()
                }
            },
            tabEnabled = state !is AnalysisState.Loading,
            onOpenSettings = onOpenSettings,
        )

        StatusBannerFor(state = state)

        ImagePreview(
            imageUri = pendingUri,
            lineBoxes = if (showLineBoxes) lineBoxes else emptyList(),
            hits = hits,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        when (val s = state) {
            AnalysisState.Idle -> {
                Text(
                    text = stringResource(R.string.status_image_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is AnalysisState.Loading -> {
                Text(
                    text = stringResource(loadingLabelRes(s.stage.toLoadingStage())),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is AnalysisState.Complete -> {
                ResultPanel(
                    report = s.report,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            is AnalysisState.Error -> {
                ErrorPanel(
                    code = s.errorCode,
                    retryable = s.retryable,
                    onRetry = ::reset,
                    onGrantPermission = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                )
            }
            else -> { /* OcrDone / RuleScanned bridge — transient */ }
        }

        when (val s = state) {
            is AnalysisState.Complete -> {
                Button(
                    onClick = { ExportAction.share(context, s.report) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.action_export))
                }
            }
            else -> {}
        }
        CaptureBar(
            onCapture = ::ensurePermissionThenLaunchCamera,
            onPick = ::pickFromGallery,
            enabled = state !is AnalysisState.Loading,
        )
    }
}

@Composable
private fun StatusBannerFor(state: AnalysisState) {
    when (state) {
        AnalysisState.Idle -> StatusBanner(StatusBannerKind.Idle, text = "")
        is AnalysisState.Loading -> StatusBanner(StatusBannerKind.Loading, text = "")
        is AnalysisState.Complete -> {
            val hits = state.report.hits
            val maxSev = hits.maxOfOrNull { it.severity }
            val kind = when (maxSev) {
                Severity.Violation -> StatusBannerKind.Violation
                Severity.Warning -> StatusBannerKind.Warning
                Severity.Info -> StatusBannerKind.Warning
                null -> StatusBannerKind.Success
            }
            val text = when (kind) {
                StatusBannerKind.Success -> stringResource(R.string.status_no_violation_card)
                else -> stringResource(R.string.status_violation_count, hits.size)
            }
            StatusBanner(kind = kind, text = text)
        }
        is AnalysisState.Error -> StatusBanner(StatusBannerKind.Violation, text = state.message)
        else -> StatusBanner(StatusBannerKind.Idle, text = "")
    }
}

@Composable
private fun ErrorPanel(
    code: ErrorCode,
    retryable: Boolean,
    onRetry: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    val msgRes = when (code) {
        ErrorCode.OCR_UNAVAILABLE -> R.string.error_ocr_unavailable
        ErrorCode.OCR_FAILED -> R.string.error_ocr_failed
        ErrorCode.RULES_FAILED -> R.string.error_rules_failed
        ErrorCode.UNKNOWN -> R.string.error_unknown
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(msgRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (retryable) {
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            TextButton(onClick = onGrantPermission) {
                Text(stringResource(R.string.action_grant_permission))
            }
        }
    }
}

private fun AnalysisState.Loading.Stage.toLoadingStage(): AnalysisStateLoadingStage = when (this) {
    AnalysisState.Loading.Stage.OcrRunning -> AnalysisStateLoadingStage.OcrRunning
    AnalysisState.Loading.Stage.RuleScanning -> AnalysisStateLoadingStage.RuleScanning
}