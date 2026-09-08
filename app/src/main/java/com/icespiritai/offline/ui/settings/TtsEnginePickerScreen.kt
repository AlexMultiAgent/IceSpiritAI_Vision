package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
 * - 顶部 TopAppBar:← 返回 + "选择 TTS 引擎" title(headlineMedium,Phase 3 §6.1)
 * - engines.isNotEmpty():RadioButton list,第一项固定 "跟随系统默认"(enginePackage=null)
 * - engines.isEmpty():empty state(两方案,见 EmptyTtsState composable)
 * - 进 picker 时外部传 engines via parameter(本 task 默认空 list,Task 11 接 probe)
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
                        style = MaterialTheme.typography.headlineMedium,
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
                    .padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    EngineRow(
                        label = stringResource(R.string.tts_engine_follow_system),
                        selected = currentEnginePackage == null,
                        onClick = { onSelectEngine(null) },
                    )
                }
                items(engines) { engine ->
                    EngineRow(
                        label = engine.label,
                        selected = engine.packageName == currentEnginePackage,
                        onClick = { onSelectEngine(engine.packageName) },
                    )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyTtsState(
    onDownloadEngine: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
        Button(
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
