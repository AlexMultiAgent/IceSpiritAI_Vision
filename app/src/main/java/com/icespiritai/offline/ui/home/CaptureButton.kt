package com.icespiritai.offline.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.icespiritai.offline.R

@Composable
fun CaptureButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // ExtendedFloatingActionButton does not expose an `enabled` parameter.
    // Preserve the public API by no-oping the click when disabled, and
    // emit the `disabled` semantic so TalkBack / Espresso see a non-clickable
    // node (matches the pre-refactor Button behaviour pinned by
    // CaptureButtonTest).
    val effectiveOnClick = if (enabled) onClick else ({})
    val a11y = stringResource(R.string.capture_button_desc)
    val baseModifier = if (enabled) modifier else modifier.semantics { disabled() }
    ExtendedFloatingActionButton(
        onClick = effectiveOnClick,
        expanded = true,
        icon = {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
            )
        },
        text = {
            Text(text = stringResource(R.string.extended_fab_label))
        },
        modifier = baseModifier.semantics { contentDescription = a11y },
    )
}
