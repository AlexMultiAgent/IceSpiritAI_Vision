package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

@Composable
fun CaptureBar(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val captureA11y = stringResource(R.string.capture_button_desc)
    val pickA11y = stringResource(R.string.select_image_button_desc)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureButton(
            onClick = onCapture,
            enabled = enabled,
            modifier = Modifier
                .weight(2f)
                .semantics { contentDescription = captureA11y },
        )
        OutlinedButton(
            onClick = onPick,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = pickA11y },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                Text(text = stringResource(R.string.action_pick_image))
            }
        }
    }
}