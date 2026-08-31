package com.icespiritai.offline.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.domain.TextNormalizer
import com.icespiritai.offline.domain.severityRank
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

/**
 * Bottom half of [ViewerScreen]. Renders one scrollable row per OCR
 * [TextLine] (text + per-line confidence) so the user can read the full
 * OCR result without taking their fingers off the zoomed image.
 *
 * Empty state shows [R.string.viewer_empty] centered (Viewer routes here
 * only when there's a result; the empty case is a defensive guard for
 * nav-graph edges where lineBoxes may be cleared between transitions).
 *
 * v0.1.41 (2026-08-31) — user feedback after v0.1.40:
 *  - Each row that contains a hit is **tinted with the worst (non-Positive)
 *    severity's container color** (violation = red, warning = amber, info =
 *    blue) so the row visually maps to the box overlay on the image above.
 *  - **Matched substrings within the row are highlighted** with the
 *    matching hit's severity container color so the user can see which
 *    words/phrases actually triggered the rule — not just which line
 *    they're on.
 *  - Containment check runs on [TextNormalizer.forMatching] so full-width
 *    vs ASCII / whitespace differences are tolerated, matching the rule
 *    engine's Aho-Corasick containment check.
 *  - Lines with no hits render with the plain surface color and no inline
 *    highlights — keeps the (typically) majority of OCR output visually
 *    quiet.
 *
 * @param lineBoxes per-line OCR output; one row per item
 * @param hits rule hits; drives both row tint and substring highlight.
 *   `emptyList()` is fine when there are no matches.
 * @param hitsCount pre-computed count for the header text. Convenience —
 *   pass `hits.size` if you don't already have a count.
 */
@Composable
fun ViewerTextList(
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    hitsCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.viewer_lines_count, lineBoxes.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.viewer_hits_count, hitsCount),
                style = MaterialTheme.typography.labelLarge,
                color = if (hitsCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        HorizontalDivider()

        if (lineBoxes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.viewer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Index-based key: OCR commonly emits the same word in
                // multiple boxes (e.g. "门店" twice on a sign), so a
                // `key = { it.text }` would crash LazyColumn with
                // "Key X was already used" the moment the list scrolls.
                itemsIndexed(
                    items = lineBoxes,
                    key = { index, _ -> index },
                ) { _, line ->
                    val rowSeverity = worstSeverityForLine(line, hits)
                    ViewerTextRow(
                        line = line,
                        hits = hits,
                        rowSeverity = rowSeverity,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerTextRow(
    line: TextLine,
    hits: List<RuleHit>,
    rowSeverity: Severity?,
) {
    // Defer reading `iceSpiritSeverityColors` until a row actually has a
    // hit — tests that wrap ViewerTextList in plain MaterialTheme (without
    // IceSpiritVisionTheme) still compose cleanly when `hits = emptyList()`.
    val bg: Color
    val onBg: Color
    if (rowSeverity != null) {
        val sev = iceSpiritSeverityColors
        bg = sev.container(rowSeverity)
        onBg = sev.onContainer(rowSeverity)
    } else {
        bg = MaterialTheme.colorScheme.surface
        onBg = MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        tonalElevation = if (rowSeverity != null) 0.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = buildLineAnnotatedString(line, hits),
                style = MaterialTheme.typography.bodyMedium,
                color = onBg,
            )
            Text(
                text = "%.0f%%".format(line.confidence * 100f),
                style = MaterialTheme.typography.labelSmall,
                color = if (rowSeverity != null) {
                    onBg.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun buildLineAnnotatedString(line: TextLine, hits: List<RuleHit>): AnnotatedString {
    val matches = highlightMatchedSubstrings(line, hits)
    if (matches.isEmpty()) return AnnotatedString(line.text)
    // Match list is non-empty — safe to read the severity-color CompositionLocal.
    val sev = iceSpiritSeverityColors
    return buildAnnotatedString {
        append(line.text)
        matches.forEach { (range, severity) ->
            addStyle(
                style = SpanStyle(background = sev.container(severity)),
                start = range.first,
                end = range.last + 1,
            )
        }
    }
}

/**
 * Worst (non-Positive) severity that matches this line, or `null` if no
 * hit applies. Positive hits are filtered out so they cannot escalate the
 * row tint (Positive hits surface through a separate KPI bucket if/when
 * one ships).
 *
 * Containment runs on [TextNormalizer.forMatching] output so whitespace /
 * case / full-width differences are tolerated, matching the rule engine.
 */
internal fun worstSeverityForLine(line: TextLine, hits: List<RuleHit>): Severity? {
    if (hits.isEmpty()) return null
    val normLine = TextNormalizer.forMatching(line.text)
    if (normLine.isEmpty()) return null
    return hits
        .asSequence()
        .filter { it.severity != Severity.Positive }
        .filter { normLine.contains(TextNormalizer.forMatching(it.matchedText)) }
        .maxByOrNull { severityRank(it.severity) }
        ?.severity
}

/**
 * Every hit whose normalized matchedText appears in the line, paired with
 * each occurrence's original-text range. Empty list means no hits matched
 * the line. Matches do not overlap — each `from` advances past the previous
 * match so the same hit can't double-paint a substring.
 */
internal fun highlightMatchedSubstrings(
    line: TextLine,
    hits: List<RuleHit>,
): List<Pair<IntRange, Severity>> {
    if (hits.isEmpty()) return emptyList()
    val normLine = TextNormalizer.forMatching(line.text)
    if (normLine.isEmpty()) return emptyList()
    val out = mutableListOf<Pair<IntRange, Severity>>()
    hits.forEach { hit ->
        val normHit = TextNormalizer.forMatching(hit.matchedText)
        if (normHit.isEmpty()) return@forEach
        var from = 0
        while (from <= normLine.length - normHit.length) {
            val idx = normLine.indexOf(normHit, from)
            if (idx < 0) break
            val range = mapNormRangeToOriginal(line.text, idx, normHit.length)
            if (range != null) {
                out.add(range to hit.severity)
            }
            from = idx + normHit.length
        }
    }
    return out
}

/**
 * Map a `[normStart, normStart + normLength)` range in the normalized text
 * back to the corresponding range in [original]. Returns `null` when the
 * range can't be mapped (out of bounds, degenerate inputs, etc.).
 *
 * `TextNormalizer.forMatching` strips only whitespace chars; the NFKC +
 * lowercase passes do **not** change the *character count* of the input,
 * so for the current rule library's CJK + ASCII keywords the invariants
 * hold and the simple "skip whitespace in original" walk below is correct.
 *
 * **Future-proofing note**: if a rule ever introduces Latin/CJK
 * compatibility characters that NFKC expands (e.g. U+FB01 `ﬁ` ligature,
 * full-width `Ａ` → ASCII `A`), the assumption breaks — an index in the
 * normalized string no longer maps 1:1 to an index in the original. At
 * that point `TextNormalizer.forMatching` should be reworked to return a
 * parallel `IntArray` of original indices, and this function should index
 * into that array instead of walking the original string.
 */
internal fun mapNormRangeToOriginal(
    original: String,
    normStart: Int,
    normLength: Int,
): IntRange? {
    if (normStart < 0 || normLength < 0) return null
    if (normLength == 0) return null
    val normEndExclusive = normStart + normLength
    var origStart = -1
    var origEndExclusive = -1
    var normIdx = 0
    var origIdx = 0
    while (origIdx < original.length) {
        if (!original[origIdx].isWhitespace()) {
            if (origStart < 0 && normIdx == normStart) {
                origStart = origIdx
            }
            normIdx++
            if (normIdx == normEndExclusive) {
                origEndExclusive = origIdx + 1
                break
            }
        }
        origIdx++
    }
    if (origStart < 0 || origEndExclusive < 0) return null
    return origStart until origEndExclusive
}
