package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
 * Material 3 [BottomAppBar] housing the home-screen affordances.
 *
 * **v0.1.41 (2026-08-31) layout** — user feedback after v0.1.40:
 *  - **3 buttons when [hasHits] is true**: pick (left), export (center), capture
 *    (right). Each gets `Modifier.weight(1f)` so widths are equal thirds.
 *  - **2 buttons when [hasHits] is false**: the export button is hidden
 *    entirely (no empty slot, no disabled state). Pick + capture take half-width
 *    each.
 *  - **Export button is rename to "导出"** (was "导出取证包" in v0.1.40). The
 *    visible label is the shorter verb; the accessibility
 *    `export_button_desc` keeps the full "导出取证包" so TalkBack still
 *    announces the action verb ("export evidence package") rather than the
 *    ambiguous "导出".
 *
 * Earlier (v0.1.31 → v0.1.40) layout, kept intact:
 *  - Pick FAB: text-then-icon. Empty icon slot + Row in text slot gives us
 *    "选图 → PhotoLibrary" without inheriting the icon→text padding the FAB
 *    would normally insert.
 *  - Capture FAB: Extended FAB (icon-then-text "拍照"), stretched to fill
 *    its half of the bar in the 2-button case.
 *
 * `enabled = false` is intentionally only forwarded to the capture FAB — the
 * pick-from-gallery action stays clickable during Loading as an escape hatch.
 * The export FAB inherits the same `enabled` flag (we don't want to call
 * ExportAction during Loading, when the report isn't ready).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureBar(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    onExport: () -> Unit,
    hasHits: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pickA11y = stringResource(R.string.pick_image_fab_desc)
    val exportA11y = stringResource(R.string.export_button_desc)
    BottomAppBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        // Pick — left slot. Always present.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            ExtendedFloatingActionButton(
                onClick = onPick,
                expanded = true,
                icon = { /* empty — see text slot below */ },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = stringResource(R.string.action_pick_image))
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.semantics { contentDescription = pickA11y },
            )
        }

        // Export — middle slot. Only when there are hits. With `enabled`
        // mirroring the capture FAB so it can't fire during Loading.
        if (hasHits) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ExtendedFloatingActionButton(
                    onClick = onExport,
                    expanded = true,
                    icon = { /* empty — see text slot below */ },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = stringResource(R.string.action_export))
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = exportA11y },
                )
            }
        }

        // Capture — right slot. Always present.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            CaptureButton(
                onClick = onCapture,
                enabled = enabled,
                // 2-button layout: stretch the capture FAB to fill its half
                // of the bar (per v0.1.31 user spec). 3-button layout: weight
                // 1f above already bounds it; fillMaxWidth stays correct
                // because both weights are equal.
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}