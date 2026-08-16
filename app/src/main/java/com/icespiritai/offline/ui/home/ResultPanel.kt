package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.ViolationReport

@Composable
fun ResultPanel(
    report: ViolationReport,
    modifier: Modifier = Modifier,
) {
    // No text at all (portrait / landscape / textless screenshot): report the
    // absence of content instead of claiming "no violation found", which would
    // imply the text was actually reviewed.
    if (!report.hasText) {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.status_no_text_card),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        return
    }

    if (report.hits.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OcrTextHeader(report)
            Text(
                text = stringResource(R.string.status_no_violation_card),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        return
    }

    // Keep the whole result scrollable so a long OCR transcript can never push
    // the hit cards off-screen.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OcrTextHeader(report)
        }
        items(report.hits) { hit -> HitCard(hit = hit) }
    }
}

@Composable
private fun OcrTextHeader(report: ViolationReport) {
    Column {
        Text(
            text = stringResource(R.string.ocr_text_label, report.ocrText),
            style = MaterialTheme.typography.bodySmall,
        )
        if (report.lowConfidence) {
            Text(
                text = stringResource(R.string.status_low_confidence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
