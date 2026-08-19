package com.icespiritai.offline.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.export.ExportAction
import java.io.File

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    /**
     * Optional Viewer router. When provided AND the analyzer has produced at
     * least one OCR line, double-tapping the preview invokes this callback.
     * Defaulted to a no-op so the existing NavHost call site stays unchanged
     * in this task — Task 12 wires the real navigation in IceSpiritNavHost.
     */
    onOpenViewer: () -> Unit = {},
    /**
     * Injectable ViewModel. `IceSpiritNavHost` passes a single
     * Activity-scoped instance so the Viewer route (a sibling destination
     * in the NavHost) can read the same `state` + `pendingUri` flows that
     * HomeScreen writes. The default `viewModel()` keeps the existing
     * Robolectric tests (`HomeScreenTest`, `HomeScreenScreenshotTest`)
     * working — those tests don't stand up a NavHost, so they get a
     * fresh ViewModel via `LocalViewModelStoreOwner.current`.
     */
    viewModel: IceSpiritVisionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val cameraDeniedMsg = stringResource(R.string.error_camera_denied)

    val selectedTab by viewModel.currentTab.collectAsState()
    // pendingUri persists across Loading→Complete so the image stays visible
    // before the analyzer finishes (AnalysisState.Loading has no URI field).
    // Lives on the ViewModel (not `remember` here) so the Viewer composable
    // — a sibling NavHost destination — can read the same value via
    // `viewModel.pendingUri.collectAsState()`.
    val pendingUri by viewModel.pendingUri.collectAsState()
    // Holds the FileProvider URI between launching the camera and receiving
    // its result. Cleared once the TakePicture callback fires (success or not).
    // Stays local because only the camera launcher reads it.
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setPendingUri(uri)
            viewModel.startAnalysis(uri)
        }
    }

    fun pickFromGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            viewModel.setPendingUri(uri)
            viewModel.startAnalysis(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingCaptureUri
        if (granted && uri != null) {
            takePictureLauncher.launch(uri)
        } else {
            pendingCaptureUri = null
            Toast.makeText(context, cameraDeniedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCapture() {
        val captureDir = File(context.cacheDir, "capture").apply { mkdirs() }
        val captureFile = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            captureFile,
        )
        pendingCaptureUri = uri
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun reset() {
        // viewModel.reset() now also clears pendingUri alongside the
        // analysis state, so the previous local `pendingUri = null` is
        // folded into the VM reset path.
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
                if (viewModel.setTab(tab)) reset()
            },
            tabEnabled = state !is AnalysisState.Loading,
            onOpenSettings = onOpenSettings,
        )

        StatusBannerFor(state = state)

        ImagePreview(
            imageUri = pendingUri,
            lineBoxes = if (showLineBoxes) lineBoxes else emptyList(),
            hits = hits,
            onDoubleTap = onOpenViewer,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        when (val s = state) {
            AnalysisState.Idle -> {
                // ImagePreview already centers `status_image_hint` when no URI is
                // loaded, so the Idle slot is empty here to avoid a duplicate
                // "请对正图片后点击拍照" overlay.
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
                    onRetry = { pendingUri?.let(viewModel::startAnalysis) },
                    onReset = ::reset,
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
            onCapture = ::launchCapture,
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
            val report = state.report
            val hits = report.hits
            if (!report.hasText) {
                StatusBanner(
                    StatusBannerKind.Warning,
                    text = stringResource(R.string.status_no_text_banner),
                )
            } else {
                val maxSev = hits.maxOfOrNull { it.severity }
                val kind = when (maxSev) {
                    Severity.Violation -> StatusBannerKind.Violation
                    Severity.Warning -> StatusBannerKind.Warning
                    Severity.Info -> StatusBannerKind.Warning
                    null -> StatusBannerKind.Success
                }
                val text = when (kind) {
                    StatusBannerKind.Success -> stringResource(R.string.status_no_violation_card)
                    else -> {
                        val violations = hits.count { it.severity == Severity.Violation }
                        val warnings = hits.count { it.severity == Severity.Warning }
                        when {
                            violations > 0 ->
                                stringResource(R.string.status_violation_count, violations)
                            warnings > 0 ->
                                stringResource(R.string.status_warning_count, warnings)
                            else -> stringResource(R.string.status_info_count, hits.size)
                        }
                    }
                }
                StatusBanner(kind = kind, text = text)
            }
        }
        is AnalysisState.Error -> StatusBanner(
            StatusBannerKind.Violation,
            text = stringResource(R.string.status_error_banner),
        )
        else -> StatusBanner(StatusBannerKind.Idle, text = "")
    }
}

@Composable
private fun ErrorPanel(
    code: ErrorCode,
    retryable: Boolean,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(errorMessageRes(code)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (retryable) {
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            } else {
                // Non-retryable (packaging defect, e.g. missing rules): the only
                // useful escape hatch is going back to pick a new image.
                TextButton(onClick = onReset) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

private fun errorMessageRes(code: ErrorCode): Int = when (code) {
    ErrorCode.OCR_UNAVAILABLE -> R.string.error_ocr_unavailable
    ErrorCode.OCR_FAILED -> R.string.error_ocr_failed
    ErrorCode.RULES_FAILED -> R.string.error_rules_failed
    ErrorCode.UNKNOWN -> R.string.error_unknown
}

private fun AnalysisState.Loading.Stage.toLoadingStage(): AnalysisStateLoadingStage = when (this) {
    AnalysisState.Loading.Stage.OcrRunning -> AnalysisStateLoadingStage.OcrRunning
    AnalysisState.Loading.Stage.RuleScanning -> AnalysisStateLoadingStage.RuleScanning
}

@Composable
fun HomeScreenBare(onCapture: () -> Unit, onPick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("冰灵锐目", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "请对正图片后点击拍照",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        CaptureBar(onCapture = onCapture, onPick = onPick)
    }
}
