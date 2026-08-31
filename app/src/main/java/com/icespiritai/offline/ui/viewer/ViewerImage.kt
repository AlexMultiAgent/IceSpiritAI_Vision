package com.icespiritai.offline.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.home.HighlightOverlay
import com.icespiritai.offline.ui.home.computeFitTransform
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

/**
 * Top half of [ViewerScreen]. Loads [imageUri] through Telephoto
 * [me.saket.telephoto.zoomable.coil.ZoomableAsyncImage] (pinch / pan /
 * double-tap-zoom) and overlays the OCR-anchored hit boxes on top so the
 * user can pinch-zoom in and see exactly which characters triggered which
 * rule.
 *
 * Phase 3.5 (2026-08-31) added the [HighlightOverlay] sibling. The
 * transform is recomputed on every (boxSize, imageSize, painter) change
 * — same precedence rules as `HomeScreen.ImagePreview`
 * ([computeFitTransform] KDoc).
 *
 * Telephoto's gesture pipeline lives in native Compose pointer input — it
 * cannot be exercised by Robolectric unit tests. Manual on-device verification
 * is the only signal here; see docs/superpowers/specs/2026-08-19-icevision-image-viewer-design.md
 * §6 acceptance checklist.
 *
 * @param imageUri URI of the analyzed image (from FileProvider capture or
 *   gallery picker). The same URI used by `ImagePreview`.
 * @param lineBoxes per-line OCR output; drives the highlight rects.
 * @param hits rule hits from the analyzer; the highlight color is the
 *   severity color of the matching hit (Violation → red, Warning → amber,
 *   Info → blue).
 * @param imageSize display-oriented dims of the FULL bitmap the OCR
 *   engine processed (the same dims `ImagePreview` uses for its
 *   HighlightOverlay). Null when unavailable (idle / loading / shell profile).
 */
@Composable
fun ViewerImage(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    imageSize: IntSize?,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { boxSize = it }
            .testTag("viewer_image"),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null) {
            Text(
                text = stringResource(R.string.viewer_image_load_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            // AsyncImage drives `imagePainter` for the transform fallback
            // path. The actual visible content is the Telephoto
            // ZoomableAsyncImage layered on top — they're sibling Box
            // children, AsyncImage is just a side-effect carrier for the
            // Coil painter state.
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag("viewer_image_painter_source"),
                onSuccess = { result -> imagePainter = result.painter },
            )
            ZoomableAsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.viewer_image_cd),
                modifier = Modifier.fillMaxSize().testTag("viewer_image_zoomable"),
            )
            if (lineBoxes.isNotEmpty()) {
                val transform = remember(boxSize, imageSize, imagePainter) {
                    computeFitTransform(imagePainter, boxSize, imageSize)
                }
                HighlightOverlay(
                    lines = lineBoxes,
                    hits = hits,
                    scaleX = transform.scaleX,
                    scaleY = transform.scaleY,
                    offsetX = transform.offsetX,
                    offsetY = transform.offsetY,
                    modifier = Modifier.fillMaxSize().testTag("viewer_image_overlay"),
                )
            }
        }
    }
}