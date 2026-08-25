package com.icespiritai.offline.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState

/**
 * Maps an [AnalysisState.Loading.Stage] to its user-visible label string
 * resource. Used by [HomeScreen] to render the running-phase hint beneath
 * the capture bar.
 */
fun loadingLabelRes(stage: AnalysisState.Loading.Stage): Int = when (stage) {
    AnalysisState.Loading.Stage.OcrRunning -> R.string.status_ocr_running
    AnalysisState.Loading.Stage.RuleScanning -> R.string.status_rule_scanning
}

/**
 * Skeleton placeholder shown by [HomeScreen] while [AnalysisState.Loading] is
 * the current analyzer state. Renders three rounded-rect "hit card" ghosts
 * with a soft diagonal shimmer (alpha 0.3 → 1.0 → 0.3) on the inner text
 * bars, plus a phase label beneath. The visual intent is "results are
 * loading" — not a spinner.
 *
 * Replaces the plain `Text(stringResource(loadingLabelRes(...)))` slot in
 * HomeScreen's Loading branch (Task 18 wires the actual call site). For now
 * the Composable is structurally complete but uncalled by any production
 * code — `HomeScreen.kt:178` still uses [loadingLabelRes] directly.
 *
 * Callers are expected to pass `Modifier.weight(1f).fillMaxWidth()` so the
 * overlay occupies the same vertical slot as the eventual `ResultPanel`.
 */
@Composable
fun LoadingOverlay(
    phase: AnalysisState.Loading.Stage,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "loadingOverlayShimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingOverlayShimmerAlpha",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(3) {
            SkeletonHitCard(shimmerAlpha = shimmerAlpha)
        }
        Text(
            text = stringResource(loadingLabelRes(phase)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Single skeleton row: a rounded-rect card the same height as a real
 * `HitCard`, with a wide "headline" bar (top-start) and a narrower
 * "secondary" bar (bottom-start) that pulse together via [shimmerAlpha].
 */
@Composable
private fun SkeletonHitCard(shimmerAlpha: Float) {
    val barColor: Color = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .fillMaxWidth(0.7f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor.copy(alpha = shimmerAlpha)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth(0.4f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor.copy(alpha = shimmerAlpha)),
        )
    }
}
