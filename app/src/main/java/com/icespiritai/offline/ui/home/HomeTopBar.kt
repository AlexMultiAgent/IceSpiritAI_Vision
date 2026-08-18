package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.icespiritai.offline.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    selectedTab: RuleTab,
    onSelectTab: (RuleTab) -> Unit,
    tabEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            actions = {
                val a11y = stringResource(R.string.settings_button_desc)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = a11y },
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                }
            },
        )
        RuleTabBar(
            selected = selectedTab,
            onSelect = onSelectTab,
            enabled = tabEnabled,
        )
    }
}
