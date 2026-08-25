package com.icespiritai.offline.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.domain.TextNormalizer
import com.icespiritai.offline.ui.theme.IceMotion
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

@Composable
fun HighlightOverlay(
    lines: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
) {
    val sev = iceSpiritSeverityColors
    val strokePx = 6f  // bumped from 4f for visual weight (Phase 3.3)
    val alpha by animateFloatAsState(
        targetValue = if (lines.isNotEmpty() && hits.isNotEmpty()) 1f else 0f,
        animationSpec = tween(
            durationMillis = IceMotion.Default.standardDuration.inWholeMilliseconds.toInt(),
            easing = IceMotion.Default.standardEasing,
        ),
        label = "highlightAlpha",
    )
    // Keywords are matched on normalized text (whitespace/full-width removed),
    // so the containment check must run on normalized lines as well — otherwise
    // "100%有效" in a line would not match the "100% 有效" hit.
    val normalizedHits = hits.map { TextNormalizer.forMatching(it.matchedText) to it.severity }
    Canvas(modifier = modifier) {
        lines.forEach { line ->
            val normalizedLine = TextNormalizer.forMatching(line.text)
            // FIXME Task 11: Severity enum is currently [Info, Warning, Violation, Positive];
            // maxOfOrNull uses Comparable (ordinal-based), so Positive wins over Violation.
            // Reorder enum to [Violation, Warning, Info, Positive] before Positive hits get emitted.
            val lineSeverity = normalizedHits
                .filter { normalizedLine.contains(it.first) }
                .maxOfOrNull { it.second }
                ?: return@forEach
            val color = sev.accent(lineSeverity)
            val x = offsetX + line.box.left * scaleX
            val y = offsetY + line.box.top * scaleY
            val w = line.box.width() * scaleX
            val h = line.box.height() * scaleY
            // Animated gradient stroke — diagonal, accent → accent@60%
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.6f),
                    ),
                    start = Offset(x, y),
                    end = Offset(x + w, y + h),
                ),
                topLeft = Offset(x, y),
                size = Size(w, h),
                style = Stroke(width = strokePx),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
