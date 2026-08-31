package com.icespiritai.offline.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors
import kotlinx.coroutines.launch

/** Public kinds — keeps backwards compat for any external callers. */
enum class StatusBannerKind { Idle, Loading, Success, Warning, Violation }

/** Sub-state for Loading — drives the running-phase text. */
enum class StatusBannerStage { LoadingOcr, LoadingRuleScanning }

/**
 * Modernized status banner (Phase 3.2, refined in 3.5): four-segment KPI
 * horizontal bar.
 * Idle shows the empty hint; Loading shows spinner + phase text; numeric
 * kinds (Violation/Warning/Success) show the three counters via
 * AnimatedContent so a hit landing after Idle triggers a slide-in.
 *
 * Phase 3.5 (2026-08-31) adds **long-press tooltips** on each KPI cell that
 * explain what the severity bucket means — short, single-sentence, fitting
 * the popup width. The 3 KPIs collapse to a single Row so users can long-
 * press each cell to learn the meaning of 违规 / 警告 / 信息 without leaving
 * the screen.
 */
@Composable
fun StatusBanner(
    kind: StatusBannerKind,
    modifier: Modifier = Modifier,
    violationCount: Int = 0,
    warningCount: Int = 0,
    infoCount: Int = 0,
    stage: StatusBannerStage? = null,
) {
    // Defer reading `iceSpiritSeverityColors` until the numeric kinds need it —
    // Idle / Loading render from plain MaterialTheme tokens and would otherwise
    // throw `LocalSeverityColors not provided` if a test rendered HomeScreen
    // without wrapping in `IceSpiritVisionTheme` (Robolectric HomeScreenTest
    // only wraps MaterialTheme).
    val bg: Color
    val accent: Color
    val onBg: Color
    when (kind) {
        StatusBannerKind.Idle, StatusBannerKind.Loading -> {
            bg = MaterialTheme.colorScheme.surfaceVariant
            accent = MaterialTheme.colorScheme.onSurfaceVariant
            onBg = MaterialTheme.colorScheme.onSurfaceVariant
        }
        StatusBannerKind.Success -> {
            val sev = iceSpiritSeverityColors
            bg = sev.container(Severity.Positive)
            accent = sev.accent(Severity.Positive)
            onBg = sev.onContainer(Severity.Positive)
        }
        StatusBannerKind.Warning -> {
            val sev = iceSpiritSeverityColors
            bg = sev.container(Severity.Warning)
            accent = sev.accent(Severity.Warning)
            onBg = sev.onContainer(Severity.Warning)
        }
        StatusBannerKind.Violation -> {
            val sev = iceSpiritSeverityColors
            bg = sev.container(Severity.Violation)
            accent = sev.accent(Severity.Violation)
            onBg = sev.onContainer(Severity.Violation)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { contentDescription = statusBannerA11y(kind, violationCount, warningCount, infoCount) },
    ) {
        when (kind) {
            StatusBannerKind.Idle -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = accent,
                )
                Text(
                    text = stringResource(R.string.empty_idle_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBg,
                )
            }
            StatusBannerKind.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 3.dp,
                    color = accent,
                )
                val phaseText = when (stage) {
                    StatusBannerStage.LoadingOcr -> stringResource(R.string.loading_ocr_skeleton)
                    StatusBannerStage.LoadingRuleScanning -> stringResource(R.string.loading_rule_skeleton)
                    null -> ""
                }
                Text(
                    text = phaseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBg,
                )
            }
            else -> KpiRow(
                violationCount = violationCount,
                warningCount = warningCount,
                infoCount = infoCount,
                accent = accent,
                onBg = onBg,
            )
        }
    }
}

@Composable
private fun KpiRow(
    violationCount: Int,
    warningCount: Int,
    infoCount: Int,
    accent: Color,
    onBg: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpiCell(
            count = violationCount,
            label = stringResource(R.string.kpi_violation_label),
            accent = accent,
            onBg = onBg,
            tooltipText = stringResource(R.string.kpi_tooltip_violation),
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = accent) },
        )
        KpiCell(
            count = warningCount,
            label = stringResource(R.string.kpi_warning_label),
            accent = accent,
            onBg = onBg,
            tooltipText = stringResource(R.string.kpi_tooltip_warning),
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = accent) },
        )
        KpiCell(
            count = infoCount,
            label = stringResource(R.string.kpi_info_label),
            accent = accent,
            onBg = onBg,
            tooltipText = stringResource(R.string.kpi_tooltip_info),
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = accent) },
        )
    }
}

/**
 * Single-line KPI cell (Phase 3.5): [icon] [count] [label] on one Row.
 * Wrapped in a [TooltipBox] so **clicking the cell** reveals a one-sentence
 * explanation of what the bucket means. Tooltip is persistent
 * (`isPersistent = true`) so it stays visible until the user taps it again
 * or taps outside — long-press alone was not discoverable enough for users
 * who didn't know to try it.
 *
 * Click toggle: `tooltipState.show()` if hidden, `tooltipState.dismiss()`
 * if already visible. Tap-anywhere-outside is handled by Material3's
 * persistent-tooltip default dismissal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KpiCell(
    count: Int,
    label: String,
    accent: Color,
    onBg: Color,
    tooltipText: String,
    icon: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val coroutineScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        state = tooltipState,
    ) {
        Row(
            modifier = Modifier.clickable {
                // show()/dismiss() are suspend — must launch from a
                // coroutineScope. The toggle pattern: hide if visible,
                // otherwise show. Persistent tooltip stays until user
                // taps again or taps outside (Material3 default).
                if (tooltipState.isVisible) {
                    coroutineScope.launch { tooltipState.dismiss() }
                } else {
                    coroutineScope.launch { tooltipState.show() }
                }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "kpiCount",
            ) { v ->
                Text(
                    text = "$v",
                    style = MaterialTheme.typography.titleLarge,
                    color = onBg,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = onBg,
            )
        }
    }
}

private fun statusBannerA11y(
    kind: StatusBannerKind,
    v: Int,
    w: Int,
    i: Int,
): String = when (kind) {
    StatusBannerKind.Idle -> "等待拍照"
    StatusBannerKind.Loading -> "识别中"
    StatusBannerKind.Success -> "未发现违规"
    StatusBannerKind.Warning -> "警告 $w 处"
    StatusBannerKind.Violation -> "违规 $v 处,警告 $w 处,信息 $i 处"
}