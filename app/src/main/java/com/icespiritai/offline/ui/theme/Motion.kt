package com.icespiritai.offline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Motion scheme (added Phase 3.1 Task 3). Standard = 300ms / FastOutSlowIn.
 *  Emphasized = 500ms / Expressive curve for hero elements (cards, FAB, status). */
data class IceMotion(
    val standardDuration: kotlin.time.Duration = kotlin.time.Duration.parse("300ms"),
    val emphasizedDuration: kotlin.time.Duration = kotlin.time.Duration.parse("500ms"),
    val standardEasing: Easing = FastOutSlowInEasing,
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
) {
    companion object {
        val Default = IceMotion()
    }
}

/** Enter animation: scale 0.95 → 1.0 + fade 0 → 1 over [IceMotion.emphasizedDuration]. */
fun Modifier.emphasizedEnter(): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = tween(
            durationMillis = IceMotion.Default.emphasizedDuration.inWholeMilliseconds.toInt(),
            easing = IceMotion.Default.emphasizedEasing,
        ),
        label = "emphasizedEnterScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = IceMotion.Default.emphasizedDuration.inWholeMilliseconds.toInt(),
            easing = IceMotion.Default.emphasizedEasing,
        ),
        label = "emphasizedEnterAlpha",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
}