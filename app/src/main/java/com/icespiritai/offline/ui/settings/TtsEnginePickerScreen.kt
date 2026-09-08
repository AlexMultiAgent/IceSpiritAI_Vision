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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.icespiritai.offline.tts.EngineStatus

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
 *
 * Bug 4 fix (v0.1.61): the local sherpa-onnx engine is ALWAYS shown
 * in the list, with a status chip on the right of the label:
 *   - Installed → no chip, Check icon if selected
 *   - NeedsDownload → "下载" chip
 *   - Downloading → "下载中" chip
 *   - DownloadFailed → "重试" chip
 * Tapping any row goes through [onEngineClick], which the controller
 * resolves to either download (status != Installed) or select (status
 * == Installed). The empty-state Card is now a defensive fallback for
 * builds that don't bundle the local engine at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsEnginePickerScreen(
    onBack: () -> Unit,
    currentEnginePackage: String?,
    onEngineClick: (String?) -> Unit,
    engines: List<EngineInfo> = emptyList(),
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
                            status = EngineStatus.Installed,
                            selected = currentEnginePackage == null,
                            onClick = { onEngineClick(null) },
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
                            status = engine.status,
                            selected = engine.status == EngineStatus.Installed &&
                                engine.packageName == currentEnginePackage,
                            onClick = { onEngineClick(engine.packageName) },
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
private fun EngineRow(
    label: String,
    status: EngineStatus,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
        when (status) {
            EngineStatus.Installed -> {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            EngineStatus.NeedsDownload ->
                EngineStatusChip(text = stringResource(R.string.tts_status_needs_download))
            EngineStatus.Downloading ->
                EngineStatusChip(text = stringResource(R.string.tts_status_downloading))
            EngineStatus.DownloadFailed ->
                EngineStatusChip(text = stringResource(R.string.tts_status_download_failed))
        }
    }
}

@Composable
private fun EngineStatusChip(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun EmptyTtsState(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.tts_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.tts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
