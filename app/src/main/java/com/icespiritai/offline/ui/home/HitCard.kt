package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.emphasizedEnter
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

/**
 * Per-rule hit card rendered in [ResultPanel]'s LazyColumn.
 *
 * Phase 3.5 redesign (2026-08-31):
 *  - **Severity is the primary classification.** The 6.dp colored stripe
 *    + subtle gradient blend from Phase 3.3 is replaced with a **full
 *    container-color background** driven by `sev.container(hit.severity)`
 *    — `违规 / 警告 / 信息` now reads at a glance, no need to mentally
 *    map a stripe color to a bucket.
 *  - **A small severity chip** ("违规" / "警告" / "信息") sits in the
 *    top-right of the card body, so the card remains readable without
 *    color (a11y + dark mode at low brightness).
 *  - **Rule category is dropped.** It used to be displayed as
 *    "分类: 广告文案 / 极限词 / 食品功能 …" but is rule-specific and
 *    duplicates the information the `依据` (regulation) line already
 *    conveys. Severity is the bucket the user actually wants to scan.
 *  - `依据` (regulation) and the collapsible 法条原文 (lawText) stay —
 *    they're the legal evidence each card is for.
 */
@Composable
fun HitCard(hit: RuleHit, modifier: Modifier = Modifier) {
    val severityLabel = stringResource(
        when (hit.severity) {
            Severity.Violation -> R.string.hit_severity_violation
            Severity.Warning -> R.string.hit_severity_warning
            Severity.Info -> R.string.hit_severity_info
            Severity.Positive -> R.string.hit_severity_positive
        }
    )
    var lawExpanded by rememberSaveable { mutableStateOf(false) }
    val sev = iceSpiritSeverityColors
    val accent = sev.accent(hit.severity)
    val container = sev.container(hit.severity)
    val onContainer = sev.onContainer(hit.severity)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "${hit.matchedText}, $severityLabel"
            }
            .emphasizedEnter(),
        colors = CardDefaults.cardColors(
            // Full severity-tinted background instead of the previous
            // surfaceContainerHigh + 6.dp stripe. The card itself now
            // visually conveys the bucket without needing a side stripe.
            containerColor = container,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "\"${hit.matchedText}\"",
                    style = MaterialTheme.typography.headlineSmall,
                    color = onContainer,
                    modifier = Modifier.weight(1f, fill = true),
                )
                // Severity chip — small pill at the top-right. Self-contained
                // signal that doesn't rely on color alone.
                SeverityChip(label = severityLabel, accent = accent, onContainer = onContainer)
            }
            Text(
                text = stringResource(R.string.hit_card_regulation, hit.regulation),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (hit.lawText.isNotBlank()) {
                FilledTonalButton(
                    onClick = { lawExpanded = !lawExpanded },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (lawExpanded) R.string.hit_card_hide_law else R.string.hit_card_show_law,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (lawExpanded) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = hit.lawText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityChip(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = onContainer,
        )
    }
}