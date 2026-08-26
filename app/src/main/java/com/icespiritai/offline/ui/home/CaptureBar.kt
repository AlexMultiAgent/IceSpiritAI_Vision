package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
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
 * Material 3 [BottomAppBar] housing the two primary home-screen affordances:
 * a small **text-then-icon** [ExtendedFloatingActionButton] (选图) on the left
 * and the **icon-then-text** [CaptureButton] Extended FAB (拍照) on the right
 * (stretched to fill its half via `Modifier.fillMaxWidth()`).
 *
 * v0.1.31 layout (user request 2026-08-26):
 *  - Left pick FAB: per user spec "icon左边加上「选图」", label `选图` sits
 *    to the LEFT of the PhotoLibrary icon (text-then-icon order). The icon
 *    slot is intentionally empty — the text slot hosts `Row { Text; Spacer;
 *    Icon }` so the order is preserved without fighting the FAB's internal
 *    icon→text padding.
 *  - Right capture FAB: same Extended FAB component as before (icon-then-text
 *    "拍照"), but stretched with `Modifier.fillMaxWidth()` to fill its half
 *    of the bar so the buttons read asymmetrically: left compact, right
 *    longer (the capture action is the primary affordance during a real
 *    on-site inspection).
 *
 * `enabled = false` is intentionally only forwarded to the capture FAB — the
 * pick-from-gallery action stays clickable during Loading as an escape hatch.
 *
 * Robolectric / Compose UI test note: each FAB carries its contentDescription
 * on the outer modifier, which Compose UI test's merged tree collapses onto
 * the Surface node (TalkBack-style). To assert against the inner [Icon] or
 * [Text], target the merged node via `onNodeWithContentDescription` for click
 * assertions, or use `useUnmergedTree = true` for text assertions.
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
            // Pick FAB: text-then-icon. Empty icon slot + Row in text slot
            // gives us "选图 → PhotoLibrary" without inheriting the
            // icon→text padding the FAB would normally insert.
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
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            CaptureButton(
                onClick = onCapture,
                enabled = enabled,
                // Stretch the capture FAB to fill its half of the bar —
                // makes the right button noticeably longer than the left
                // pick button (per user spec 2026-08-26).
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}