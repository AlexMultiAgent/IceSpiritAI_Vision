package com.icespiritai.offline.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkWarning
import com.icespiritai.offline.ui.theme.LightError
import com.icespiritai.offline.ui.theme.LightWarning

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
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    val strokePx = 4f
    Canvas(modifier = modifier) {
        lines.forEach { line ->
            val lineSeverity = hits
                .filter { line.text.contains(it.matchedText) }
                .maxOfOrNull { it.severity }
                ?: return@forEach
            val color = when (lineSeverity) {
                Severity.Violation -> if (isDark) DarkError else LightError
                Severity.Warning -> if (isDark) DarkWarning else LightWarning
                Severity.Info -> return@forEach
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    offsetX + line.box.left * scaleX,
                    offsetY + line.box.top * scaleY,
                ),
                size = Size(
                    line.box.width() * scaleX,
                    line.box.height() * scaleY,
                ),
                style = Stroke(width = strokePx),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }
}