package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.R
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.settings.SettingsViewModel

/**
 * Modernized Settings screen (Phase 3.5 Task 21).
 *
 * Layout: each section is wrapped in a [Card]; the changelog row uses a
 * Material 3 [ListItem] with a chevron trailing icon. Top-bar title uses
 * `headlineSmall` to match HomeTopBar.
 *
 * `SettingsRepository(context.applicationContext)` keeps Robolectric-friendly
 * SharedPreferences; tests do not need a fake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenUpdateDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(SettingsRepository(context.applicationContext)),
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AppearanceSection(current = themeMode, onSelect = viewModel::setThemeMode)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                UpdateSection(viewModel = viewModel, onOpenUpdateDetail = onOpenUpdateDetail)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_view_changelog)) },
                    supportingContent = { Text(stringResource(R.string.settings_view_changelog_hint)) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onOpenChangelog),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_about_org),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}