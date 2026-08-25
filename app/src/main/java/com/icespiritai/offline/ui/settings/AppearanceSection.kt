package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = current == ThemeMode.SYSTEM,
                onClick = { onSelect(ThemeMode.SYSTEM) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                modifier = Modifier.testTag("theme_SYSTEM"),
            ) { Text(stringResource(R.string.settings_appearance_system)) }
            SegmentedButton(
                selected = current == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                modifier = Modifier.testTag("theme_DARK"),
            ) { Text(stringResource(R.string.settings_appearance_dark)) }
            SegmentedButton(
                selected = current == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                modifier = Modifier.testTag("theme_LIGHT"),
            ) { Text(stringResource(R.string.settings_appearance_light)) }
        }
    }
}