package com.icespiritai.offline.ui.home

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.icespiritai.offline.R

enum class RuleTab(val titleRes: Int) {
    AdSignage(R.string.tab_ad_law),
    FoodLabeling(R.string.tab_food_label),
}

@Composable
fun RuleTabBar(
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.tab_switch_desc)
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        RuleTab.entries.forEach { tab ->
            Tab(
                selected = (tab == selected),
                onClick = { if (enabled) onSelect(tab) },
                enabled = enabled || tab == selected,
                text = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}
