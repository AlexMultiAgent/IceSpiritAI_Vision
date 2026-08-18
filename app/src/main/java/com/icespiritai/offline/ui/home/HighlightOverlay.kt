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
import com.icespiritai.offline.domain.TextNormalizer
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
import com.icespiritai.offline.ui.theme.LightIceChatError
import com.icespiritai.offline.ui.theme.LightIceChatWarning

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
    // Keywords are matched on normalized text (whitespace/full-width removed),
    // so the containment check must run on normalized lines as well — otherwise
    // "100%有效" in a line would not match the "100% 有效" hit.
    val normalizedHits = hits.map { TextNormalizer.forMatching(it.matchedText) to it.severity }
    Canvas(modifier = modifier) {
        lines.forEach { line ->
            val normalizedLine = TextNormalizer.forMatching(line.text)
            val lineSeverity = normalizedHits
                .filter { normalizedLine.contains(it.first) }
                .maxOfOrNull { it.second }
                ?: return@forEach
            val color = when (lineSeverity) {
                Severity.Violation -> if (isDark) DarkIceChatError else LightIceChatError
                Severity.Warning -> if (isDark) DarkIceChatWarning else LightIceChatWarning
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
