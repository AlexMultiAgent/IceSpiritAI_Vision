package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

/**
 * Result panel rendered below the image preview once the analyzer reaches
 * [AnalysisState.Complete].
 *
 * Phase 3.5 redesign (2026-08-31):
 *  - **Drops the OCR-text header.** The user explicitly asked for the
 *    recognition text to be hidden — the actionable signal is the hit
 *    cards, and the OCR text is duplicated in `导出取证包 report.txt`.
 *  - **Groups hits into 3 sections by severity** (违规 / 警告 / 信息),
 *    in rank order (Violation > Warning > Info). Each section has a
 *    color-tinted header showing the bucket name + count.
 *  - **Sections that have zero hits are skipped entirely** — no "信息 (0)"
 *    placeholders. Keeps the scroll compact for the common case (most
 *    ads have only 1 or 2 severity buckets).
 */
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
            Text(
                text = stringResource(R.string.status_no_violation_card),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            // The low-confidence hint was previously rendered next to the
            // OCR text (which Phase 3.5 dropped per user request). It's
            // still useful when there are no hits — it tells the user
            // "we may have missed things" — so it lives on its own line
            // below the "no violation" message.
            if (report.lowConfidence) {
                Text(
                    text = stringResource(R.string.status_low_confidence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }

    val groups = listOf(
        Severity.Violation to report.hits.filter { it.severity == Severity.Violation },
        Severity.Warning to report.hits.filter { it.severity == Severity.Warning },
        Severity.Info to report.hits.filter { it.severity == Severity.Info },
    ).filter { it.second.isNotEmpty() }

    // Scrollable list of severity sections, each followed by its hit cards.
    // Phase 3.5 dropped the OCR-text header — the actionable signal is here.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((severity, hits) in groups) {
            item(key = "section_${severity.name}") {
                SeveritySectionHeader(severity = severity, count = hits.size)
            }
            items(hits, key = { hit -> "${severity.name}_${hit.ruleId}_${hit.matchedText}" }) { hit ->
                HitCard(hit = hit)
            }
        }
    }
}

@Composable
private fun SeveritySectionHeader(severity: Severity, count: Int) {
    val sev = iceSpiritSeverityColors
    val bucketLabel = stringResource(
        when (severity) {
            Severity.Violation -> R.string.hit_severity_violation
            Severity.Warning -> R.string.hit_severity_warning
            Severity.Info -> R.string.hit_severity_info
            Severity.Positive -> R.string.hit_severity_positive
        }
    )
    val accent = sev.accent(severity)
    val container = sev.container(severity)
    val onContainer = sev.onContainer(severity)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Severity color as a leading 4.dp vertical bar inside the box —
        // gives a quick visual key without adding layout weight.
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.result_section_header, bucketLabel, count),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
            // Subtle hint using the accent color — adds a hint of contrast
            // without needing extra strings.
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(accent.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                )
            }
        }
    }
}