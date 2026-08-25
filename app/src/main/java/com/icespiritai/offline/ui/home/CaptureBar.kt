package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

/**
 * Material 3 [BottomAppBar] housing the two primary home-screen affordances:
 * a small [FloatingActionButton] (相册) on the left and the
 * [CaptureButton] Extended FAB (拍照) on the right.
 *
 * The bar uses a transparent container + zero tonal elevation so it reads as
 * a footer overlay rather than an elevated surface, matching the modernized
 * dark / light themes established in Phase 3.1–3.3.
 *
 * `enabled = false` is intentionally only forwarded to the capture FAB — the
 * pick-from-gallery action stays clickable during Loading as an escape hatch.
 *
 * Robolectric / Compose UI test note: the pick FAB carries its
 * contentDescription on the outer modifier, which Compose UI test's merged
 * tree collapses onto the Surface node (TalkBack-style). To assert against
 * the inner [Icon], target the merged node via `onNodeWithContentDescription`
 * rather than `onNodeWithText`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureBar(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pickA11y = stringResource(R.string.pick_image_fab_desc)
    BottomAppBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            FloatingActionButton(
                onClick = onPick,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = pickA11y },
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            CaptureButton(
                onClick = onCapture,
                enabled = enabled,
            )
        }
    }
}
