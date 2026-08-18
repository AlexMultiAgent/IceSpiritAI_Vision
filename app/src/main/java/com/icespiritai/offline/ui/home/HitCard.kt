package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.components.SeverityBadge

@Composable
fun HitCard(hit: RuleHit, modifier: Modifier = Modifier) {
    val severityLabel = stringResource(
        when (hit.severity) {
            Severity.Violation -> R.string.hit_severity_violation
            Severity.Warning -> R.string.hit_severity_warning
            Severity.Info -> R.string.hit_severity_info
        }
    )
    val categoryLabel = CategoryDisplay.displayName(hit.domain, hit.category)
    var lawExpanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${hit.matchedText}, $severityLabel, $categoryLabel"
            },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hit.matchedText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SeverityBadge(severity = hit.severity)
            }
            Text(
                text = stringResource(R.string.hit_card_category, categoryLabel),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.hit_card_regulation, hit.regulation),
                style = MaterialTheme.typography.bodySmall,
            )
            if (hit.lawText.isNotBlank()) {
                TextButton(
                    onClick = { lawExpanded = !lawExpanded },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = stringResource(
                            if (lawExpanded) R.string.hit_card_hide_law else R.string.hit_card_show_law,
                        ),
                    )
                }
                if (lawExpanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = hit.lawText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
