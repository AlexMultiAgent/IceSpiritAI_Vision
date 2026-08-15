package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

@Composable
fun LoadingOverlay(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

fun loadingLabelRes(stage: AnalysisStateLoadingStage): Int = when (stage) {
    AnalysisStateLoadingStage.OcrRunning -> R.string.status_ocr_running
    AnalysisStateLoadingStage.RuleScanning -> R.string.status_rule_scanning
}

enum class AnalysisStateLoadingStage { OcrRunning, RuleScanning }