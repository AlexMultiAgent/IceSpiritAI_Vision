package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.R
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState

/**
 * Renders the changelog associated with the currently detected `UpdateAvailable`
 * state. Kept as a separate screen from [SettingsScreen] so a long changelog
 * never pushes the download button off-screen — the bug v0.1.13 shipped with.
 *
 * Source of truth is the process-global [UpdateRepository.state] (same
 * `StateFlow` [com.icespiritai.offline.settings.SettingsViewModel.updateState]
 * already exposes). If the user navigates here before the check completes
 * (or after dismissing the available card), the state is something other
 * than [UpdateState.UpdateAvailable] and we show a fallback hint rather
 * than fabricate a changelog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by UpdateRepository.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_detail_title)) },
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
        modifier = modifier,
    ) { padding ->
        val available = state as? UpdateState.UpdateAvailable
        if (available == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.update_detail_no_pending),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }

        val versionName = available.info.versionName
        val changelog = available.info.changelog

        // changelog arrives as a multi-line plain-text block from
        // LatestJsonGenerator.extractLatestChangelog; render each non-blank
        // line as its own paragraph so very long entries stay readable and
        // LazyColumn recycles offscreen lines.
        val lines = changelog.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item(key = "header") {
                Text(
                    text = stringResource(R.string.update_available_banner, versionName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
            }
            items(lines, key = { idx -> "line-$idx" }) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
        }
    }
}