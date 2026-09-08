package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.tts.EngineInfo

/**
 * Picker sub-page(spec §7.2)。
 *
 * Phase 3 Editorial visual overhaul (v0.1.61):
 * - title uses `headlineSmall` (26sp) instead of `headlineMedium` (30sp)
 *   to match the Settings top-bar (SettingsScreen.kt:77) — both pages now
 *   read as one cohesive Editorial pair instead of picker overshadowing
 *   its parent route.
 * - list container is wrapped in a `Card` with the same padding as
 *   SettingsScreen.kt:100 cards so the picker list looks identical to
 *   the rest of the Settings page.
 * - selected indicator is a primary-tinted Check icon (not Material
 *   RadioButton) — keeps the picker consistent with the no-radio mode
 *   used elsewhere in Settings (rows are clickable, not radio-group).
 * - rows separated by `HorizontalDivider` (0.5dp, colorScheme.outline)
 *   so adjacent engines read as discrete options.
 * - empty state wrapped in a Card; primary CTA is `FilledTonalButton`
 *   (Editorial palette — solid `Button` was too heavy for an empty
 *   state hint).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsEnginePickerScreen(
    onBack: () -> Unit,
    currentEnginePackage: String?,
    onSelectEngine: (String?) -> Unit,
    engines: List<EngineInfo> = emptyList(),
    onDownloadEngine: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tts_engine_picker_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (engines.isEmpty()) {
            EmptyTtsState(
                onDownloadEngine = onDownloadEngine,
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Card(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // "跟随系统默认" is always the first row (null package).
                    item {
                        EngineRow(
                            label = stringResource(R.string.tts_engine_follow_system),
                            selected = currentEnginePackage == null,
                            onClick = { onSelectEngine(null) },
                        )
                    }
                    itemsIndexed(engines) { index, engine ->
                        if (index == 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        EngineRow(
                            label = engine.label,
                            selected = engine.packageName == currentEnginePackage,
                            onClick = { onSelectEngine(engine.packageName) },
                        )
                        if (index != engines.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyTtsState(
    onDownloadEngine: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tts_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.tts_empty_solution_1_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.tts_empty_solution_1_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.tts_empty_solution_2_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.tts_empty_solution_2_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = onDownloadEngine,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isDownloading) {
                        stringResource(R.string.tts_empty_downloading, downloadProgress)
                    } else {
                        stringResource(R.string.tts_empty_download)
                    },
                )
            }
        }
    }
}
