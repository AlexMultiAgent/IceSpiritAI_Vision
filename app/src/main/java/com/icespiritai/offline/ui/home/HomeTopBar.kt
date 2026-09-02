package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icespiritai.offline.R

/**
 * Compact home header: a tight Column of { title row + tab row } on a
 * single transparent [Surface], no Material 3 [TopAppBar] involved.
 *
 * Layout notes:
 *  - The title is centered via a [Box] with two children: the
 *    "冰灵锐目" [Text] pinned at [Alignment.Center], and the settings
 *    IconButton at [Alignment.CenterEnd]. Putting both in a Box (instead
 *    of a Row with weighted spacers) keeps the centered text truly
 *    centered regardless of how wide the settings button is or how the
 *    box parent constrains its width.
 *  - The title is a single Compose [Text] reading `app_name` ("冰灵锐目")
 *    at `titleMedium.copy(fontSize = 20.sp)` — overrides size locally
 *    without touching the global `IceSpiritTypography.titleMedium` token
 *    (which is used by 7+ other files including the now-smaller tab pill
 *    text). The bolt ⚡ was removed in v0.1.48 per user feedback
 *    ("突兀"), and the prefix/suffix Text split became redundant once
 *    the bolt was gone.
 *  - Merging TopAppBar + TabRow into one Column cuts the title→tab gap
 *    to 8dp; the previous M3 default was ~16dp.
 *
 * Status-bar inset is applied via [windowInsetsPadding] on the inner
 * Column (since TopAppBar's default `windowInsets` handling is gone
 * with the TopAppBar).
 */
@Composable
fun HomeTopBar(
    selectedTab: RuleTab,
    onSelectTab: (RuleTab) -> Unit,
    tabEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val a11ySettings = stringResource(R.string.settings_button_desc)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .semantics { contentDescription = a11ySettings },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                    )
                }
            }
            RuleTabBar(
                selected = selectedTab,
                onSelect = onSelectTab,
                enabled = tabEnabled,
            )
        }
    }
}