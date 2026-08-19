package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine

private data class FitTransform(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)

private fun computeFitTransform(painter: Painter?, boxSize: IntSize): FitTransform {
    if (painter == null || boxSize == IntSize.Zero) return FitTransform(1f, 1f, 0f, 0f)
    val intrinsicW = painter.intrinsicSize.width
    val intrinsicH = painter.intrinsicSize.height
    if (intrinsicW <= 0f || intrinsicH <= 0f) return FitTransform(1f, 1f, 0f, 0f)
    val boxW = boxSize.width.toFloat()
    val boxH = boxSize.height.toFloat()
    val scale = minOf(boxW / intrinsicW, boxH / intrinsicH)
    return FitTransform(
        scaleX = scale,
        scaleY = scale,
        offsetX = (boxW - intrinsicW * scale) / 2f,
        offsetY = (boxH - intrinsicH * scale) / 2f,
    )
}

@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
    /**
     * Optional double-tap handler. When provided AND [lineBoxes] is non-empty
     * (i.e. there is an OCR result worth a Viewer screen), the preview
     * installs a [detectTapGestures] handler that fires this callback on
     * double-tap. Defaulted to `null` for existing callers — null means no
     * gesture detector is installed at all (cleaner than swallowing taps
     * silently).
     *
     * The `lineBoxes.isNotEmpty()` gate matters: a user who just took a photo
     * of a wall (or any image where OCR found nothing) should never be able
     * to "open the Viewer" because there's nothing to view. The HomeScreen
     * wires this callback only when the ViewModel has at least one OCR line.
     */
    onDoubleTap: (() -> Unit)? = null,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    val rootModifier = modifier
        .fillMaxSize()
        .testTag("image_preview")
        .let { m ->
            if (onDoubleTap != null && lineBoxes.isNotEmpty()) {
                // The Double-tap callback is hoisted — capturing [onDoubleTap]
                // keeps the lambda identity stable across recompositions.
                m.pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
            } else {
                m
            }
        }
        .semantics { contentDescription = a11y }
        .onSizeChanged { boxSize = it }
    Box(
        modifier = rootModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null) {
            Text(
                text = stringResource(R.string.status_image_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result -> imagePainter = result.painter },
            )
            if (lineBoxes.isNotEmpty()) {
                val transform = remember(boxSize, imagePainter) {
                    computeFitTransform(imagePainter, boxSize)
                }
                HighlightOverlay(
                    lines = lineBoxes,
                    hits = hits,
                    scaleX = transform.scaleX,
                    scaleY = transform.scaleY,
                    offsetX = transform.offsetX,
                    offsetY = transform.offsetY,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}