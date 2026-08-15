package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.ui.components.SeverityBadge

@Composable
fun HitCard(hit: RuleHit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "违规条目: ${hit.matchedText}, 严重等级 ${hit.severity.name}"
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
                text = stringResource(R.string.hit_card_category, hit.category),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.hit_card_regulation, hit.regulation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}