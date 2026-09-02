package com.icespiritai.offline.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
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
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.severityRank
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
    val noCameraAppMsg = stringResource(R.string.error_no_camera_app)
    val noGalleryAppMsg = stringResource(R.string.error_no_gallery_app)

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

    fun onImagePicked(uri: Uri?) {
        if (uri != null) {
            viewModel.setPendingUri(uri)
            viewModel.startAnalysis(uri)
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> onImagePicked(uri) }

    // Fallback for devices without a system photo picker (Huawei/HarmonyOS
    // without GMS). PickVisualMedia silently degrades to ACTION_OPEN_DOCUMENT
    // there, which shows the DocumentsUI file browser instead of a gallery.
    val pickLegacy = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        onImagePicked(result.data?.data.takeIf { result.resultCode == Activity.RESULT_OK })
    }

    fun pickFromGallery() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        // Both branches can throw: a stripped device may have neither an OEM
        // gallery nor the DocumentsUI that PickVisualMedia degrades to.
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                pickMedia.launch(request)
                return
            }
            // setDataAndType, not `type = ...` — Intent.setType() nulls out the
            // data URI set by the constructor.
            pickLegacy.launch(
                Intent(Intent.ACTION_PICK).setDataAndType(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "image/*",
                ),
            )
        } catch (_: ActivityNotFoundException) {
            try {
                pickMedia.launch(request)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, noGalleryAppMsg, Toast.LENGTH_SHORT).show()
            }
        }
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

    // ACTION_IMAGE_CAPTURE has no handler on devices without a camera app
    // (uses-feature camera is required="false"), and launch() throws.
    fun launchTakePicture(uri: Uri) {
        try {
            takePictureLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCaptureUri = null
            Toast.makeText(context, noCameraAppMsg, Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingCaptureUri
        if (granted && uri != null) {
            launchTakePicture(uri)
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
            launchTakePicture(uri)
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
    val lineBoxes = ocrResult?.lineBoxes ?: completeReport?.lineBoxes ?: emptyList()
    val hits = completeReport?.hits ?: emptyList()
    val showLineBoxes = (state is AnalysisState.OcrDone) || completeReport != null
    val imageSize: IntSize? = imageSizeForState(ocrResult, completeReport)
    // v0.1.41: export is gated on (Complete + hasHits). The CaptureBar
    // hides its middle button when hasHits=false, so we don't even render
    // a no-op export affordance when the analyzer found nothing.
    val hasHits = hits.isNotEmpty()
    val canExport = (state is AnalysisState.Complete) && hasHits
    val exportScope = rememberCoroutineScope()
    fun onExport() {
        val s = state as? AnalysisState.Complete ?: return
        // v0.1.43: pass the Compose-bound scope so ExportAction.share dispatches
        // the ContentResolver read + ZipOutputStream + cacheDir.writeBytes onto
        // Dispatchers.IO (50-200 ms of work for an 8 MB image — was blocking
        // the main thread when this onClick handler ran sync).
        ExportAction.share(context, s.report, BuildConfig.VERSION_NAME, exportScope)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            selectedTab = selectedTab,
            onSelectTab = { tab ->
                // VM.setTab handles reset internally per CLAUDE.md §Tab → 初始页
                // (2026-08-26):同 tab + !Loading → reset;同 tab + Loading → no-op;
                // tab 切换 → 保留 state。caller-side reset() 路径已删除,因为它会
                // 误把 tab 切换路径也走 reset 路径。
                viewModel.setTab(tab)
            },
            tabEnabled = state !is AnalysisState.Loading,
            onOpenSettings = onOpenSettings,
        )

        StatusBannerFor(state = state)

        ImagePreview(
            imageUri = pendingUri,
            lineBoxes = if (showLineBoxes) lineBoxes else emptyList(),
            hits = hits,
            imageSize = imageSize,
            onDoubleTap = onOpenViewer,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        when (val s = state) {
            AnalysisState.Idle -> {
                // ImagePreview already centers the Idle mascot when no URI is
                // loaded, so the Idle slot stays empty: StatusBanner (Idle) above
                // is the single place that states the capture instruction.
            }
            is AnalysisState.Loading -> {
                Text(
                    text = stringResource(loadingLabelRes(s.stage)),
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

        // v0.1.41: export button moved into CaptureBar (3rd middle slot
        // when hasHits=true). The standalone export Button above the
        // CaptureBar is gone — keeps the bar visually balanced and makes
        // "导出" discoverable next to the other primary actions.
        CaptureBar(
            onCapture = ::launchCapture,
            onPick = ::pickFromGallery,
            onExport = ::onExport,
            hasHits = hasHits,
            enabled = state !is AnalysisState.Loading,
        )
    }
}

@Composable
private fun StatusBannerFor(state: AnalysisState) {
    when (state) {
        AnalysisState.Idle -> StatusBanner(StatusBannerKind.Idle)
        is AnalysisState.Loading -> StatusBanner(
            kind = StatusBannerKind.Loading,
            stage = when (state.stage) {
                AnalysisState.Loading.Stage.OcrRunning -> StatusBannerStage.LoadingOcr
                AnalysisState.Loading.Stage.RuleScanning -> StatusBannerStage.LoadingRuleScanning
            },
        )
        is AnalysisState.Complete -> {
            val report = state.report
            if (!report.hasText) {
                StatusBanner(StatusBannerKind.Warning)
            } else {
                // Banner kind is driven by the WORST non-Positive hit — a
                // Positive hit on the same image must not drown out a
                // genuine Violation. Worst-rank is computed via an
                // explicit `severityRank` (not enum.ordinal — the enum
                // ordering is a presentation detail and could shift
                // without warning as new buckets are added).
                val worstViolationOrWarning = report.hits
                    .filter { it.severity != Severity.Positive }
                    .maxByOrNull { severityRank(it.severity) }
                val kind = when (worstViolationOrWarning?.severity) {
                    Severity.Violation -> StatusBannerKind.Violation
                    Severity.Warning -> StatusBannerKind.Warning
                    // No violation/warning: Info-only or Positive-only →
                    // both surface as "compliant". Phase 3.3 may split
                    // Positive into its own KPI bucket.
                    else -> StatusBannerKind.Success
                }
                StatusBanner(
                    kind = kind,
                    violationCount = report.hits.count { it.severity == Severity.Violation },
                    warningCount = report.hits.count { it.severity == Severity.Warning },
                    infoCount = report.hits.count { it.severity == Severity.Info },
                )
            }
        }
        is AnalysisState.Error -> StatusBanner(StatusBannerKind.Violation)
        else -> StatusBanner(StatusBannerKind.Idle)
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

@Composable
@VisibleForTesting
internal fun HomeScreenBare(onCapture: () -> Unit, onPick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        StatusBanner(StatusBannerKind.Idle)
        ImagePreview(
            imageUri = null,
            lineBoxes = emptyList(),
            hits = emptyList(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // Bare preview never has hits → export button hidden by CaptureBar.
        CaptureBar(
            onCapture = onCapture,
            onPick = onPick,
            onExport = {},
            hasHits = false,
        )
    }
}

/**
 * Derive the display-oriented bitmap dimensions [ImagePreview] should use
 * as the reference space for HighlightOverlay rects.
 *
 * Resolution order:
 *  1. [AnalysisState.OcrDone.imageWidth/Height] when both > 0 — this is the
 *     primary source while the user is mid-pipeline (between OCR and rules).
 *  2. [ViolationReport.imageWidth/Height] when both > 0 — fallback after
 *     state transitions to Complete (where the OcrDone AnalysisState is no
 *     longer active; the dims survive on the report).
 *  3. `null` when nothing usable — Idle / Loading states, legacy reports
 *     without dims, or any path where dims haven't been populated. ImagePreview
 *     falls back to painter.intrinsicSize in that case (covered by
 *     `ImagePreviewFitTransformTest`).
 *
 * Extracted as a pure helper (no Composable) so unit tests can pin the
 * precedence rules without driving Compose / ViewModel state.
 */
@VisibleForTesting
internal fun imageSizeForState(
    ocrResult: AnalysisState.OcrDone?,
    completeReport: ViolationReport?,
): IntSize? = when {
    ocrResult != null && ocrResult.imageWidth > 0 && ocrResult.imageHeight > 0 ->
        IntSize(ocrResult.imageWidth, ocrResult.imageHeight)
    completeReport != null && completeReport.imageWidth > 0 && completeReport.imageHeight > 0 ->
        IntSize(completeReport.imageWidth, completeReport.imageHeight)
    else -> null
}
