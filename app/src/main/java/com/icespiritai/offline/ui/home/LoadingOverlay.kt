package com.icespiritai.offline.ui.home

import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState

/**
 * Maps an [AnalysisState.Loading.Stage] to its user-visible label string
 * resource. Used by [HomeScreen] to render the running-phase hint beneath
 * the capture bar.
 */
fun loadingLabelRes(stage: AnalysisState.Loading.Stage): Int = when (stage) {
    AnalysisState.Loading.Stage.OcrRunning -> R.string.status_ocr_running
    AnalysisState.Loading.Stage.RuleScanning -> R.string.status_rule_scanning
}
