package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.emphasizedEnter
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

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
    val categoryLabel = CategoryDisplay.displayName(hit.domain, hit.category)
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
                contentDescription = "${hit.matchedText}, $severityLabel, $categoryLabel"
            }
            .emphasizedEnter(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                container.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = "\"${hit.matchedText}\"",
                    style = MaterialTheme.typography.headlineSmall,
                    color = onContainer,
                )
                Text(
                    text = stringResource(R.string.hit_card_category, categoryLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.hit_card_regulation, hit.regulation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
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
}
