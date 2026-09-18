package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine

internal data class FitTransform(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)

/**
 * Rendered size of the Idle mascot.
 *
 * A fixed dp token, deliberately **not** a fraction of the preview box. A
 * percentage couples decoration to viewport height, so the same artwork reads
 * as a tasteful placeholder on a 6" phone and as a billboard on a tablet or an
 * unfolded foldable — and the empty state is exactly the screen those layouts
 * spend the most extra height on. 45% of the box measured 774px (258dp) on a
 * 1080x2400 phone, which is roughly twice current platform practice.
 *
 * 120dp sits in the band Google's own Material 3 empty states use (120-160dp)
 * and still leaves the face and the smart glasses legible. It is also smaller
 * than the 160dp+ of the capture controls below it, so the artwork stays
 * subordinate to the action — which is the point of an empty state.
 */
private val IdleMascotSize = 120.dp

/**
 * Compute the (scale, offset) needed to render OCR boxes (whose coordinates
 * live in the FULL bitmap's display-oriented pixel space) onto [boxSize].
 *
 * Reference-dim precedence:
 *   1. [imageSize] if both width and height are > 0 — this is the FULL
 *      bitmap's display-oriented dims from the OCR engine. Using it
 *      guarantees the transform matches the coordinate space the boxes
 *      were emitted in, regardless of how Coil chose to downsample the
 *      preview bitmap to fit the layout.
 *   2. [painter]'s `intrinsicSize` (the downsampled bitmap Coil returned)
 *      as a fallback for callers that don't have the OCR dims yet (e.g.
 *      initial render before OcrDone arrives, or the FakeOcrEngine shell
 *      profile where no image is processed).
 *   3. Identity (1, 1, 0, 0) when nothing is usable.
 *
 * Visible for unit tests so `computeFitTransformTest` can pin the
 * precedence rules without driving Compose.
 */
@VisibleForTesting
internal fun computeFitTransform(
    painter: Painter?,
    boxSize: IntSize,
    imageSize: IntSize? = null,
): FitTransform {
    val boxW = boxSize.width.toFloat()
    val boxH = boxSize.height.toFloat()
    if (boxW <= 0f || boxH <= 0f) return FitTransform(1f, 1f, 0f, 0f)

    // Prefer the FULL bitmap dims from the OCR engine. painter.intrinsicSize
    // is the downsampled bitmap Coil handed Compose, which is NOT the same
    // coordinate space the boxes were emitted in — using it would put boxes
    // off-screen on every non-trivial layout (verified on the corn
    // advertisement fixture, scale collapsed to 1.0 and boxes drifted into
    // the right margin and the OCR-text panel).
    val refW: Float
    val refH: Float
    if (imageSize != null && imageSize.width > 0 && imageSize.height > 0) {
        refW = imageSize.width.toFloat()
        refH = imageSize.height.toFloat()
    } else if (painter != null) {
        refW = painter.intrinsicSize.width
        refH = painter.intrinsicSize.height
        if (!refW.isFinite() || !refH.isFinite() ||
            refW <= 0f || refH <= 0f) return FitTransform(1f, 1f, 0f, 0f)
    } else {
        return FitTransform(1f, 1f, 0f, 0f)
    }

    val scale = minOf(boxW / refW, boxH / refH)
    if (!scale.isFinite() || scale <= 0f) return FitTransform(1f, 1f, 0f, 0f)
    return FitTransform(
        scaleX = scale,
        scaleY = scale,
        offsetX = (boxW - refW * scale) / 2f,
        offsetY = (boxH - refH * scale) / 2f,
    )
}

/**
 * [computeFitTransform] 的逆运算：预览框里的触点 → 图片内的相对坐标（0..1）。
 *
 * 图片按 `min(boxW/refW, boxH/refH)` 等比缩放并居中（见 [computeFitTransform]），
 * 所以 `imageX = (viewX - offsetX) / scaleX`。触点落在图片外的信箱/黑边区时
 * 返回 `null` —— 否则用户在留白处一按，就会被当成"选了图片边缘那一块"。
 *
 * 相对坐标与分辨率无关，因此 ViewModel 可以直接拿它在原图上裁剪。
 */
@VisibleForTesting
internal fun viewOffsetToImageFraction(
    offset: Offset,
    transform: FitTransform,
    imageSize: IntSize?,
): Pair<Float, Float>? {
    if (imageSize == null || imageSize.width <= 0 || imageSize.height <= 0) return null
    val scaleX = transform.scaleX.takeIf { it.isFinite() && it > 0f } ?: return null
    val scaleY = transform.scaleY.takeIf { it.isFinite() && it > 0f } ?: return null
    val imageX = (offset.x - transform.offsetX) / scaleX
    val imageY = (offset.y - transform.offsetY) / scaleY
    if (imageX < 0f || imageY < 0f) return null
    val fractionX = imageX / imageSize.width
    val fractionY = imageY / imageSize.height
    if (fractionX > 1f || fractionY > 1f) return null
    return fractionX to fractionY
}

@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
    /**
     * Display-oriented dims of the FULL bitmap the OCR engine processed.
     * Used as the reference space for [HighlightOverlay] — see
     * [computeFitTransform] KDoc for why painter.intrinsicSize is NOT
     * reliable here. Defaulted to `null` so existing call sites
     * (tests, shell-profile previews without OCR) keep working.
     */
    imageSize: IntSize? = null,
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
    /**
     * 长按图片上的某点 → 回调该点在**图片内的相对坐标**（0..1）。
     *
     * 用于「放大识别这块 region」：相对坐标与分辨率无关，ViewModel 拿到它
     * 就能在原始图（含 OCR 用的 2048 上限解码版）上裁出对应区域。
     * 传 `null`（默认）时不装手势；与 [onDoubleTap] 共存于同一个
     * `detectTapGestures`，因此长按不会和双击打架。
     */
    onLongPressAt: ((Float, Float) -> Unit)? = null,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    val idleMascotA11y = stringResource(R.string.mascot_idle_desc)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    val rootModifier = modifier
        .fillMaxSize()
        .testTag("image_preview")
        .let { m ->
            if ((onDoubleTap != null && lineBoxes.isNotEmpty()) || onLongPressAt != null) {
                // The callbacks are hoisted — capturing them keeps the lambda
                // identity stable across recompositions.
                m.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = if (onDoubleTap != null && lineBoxes.isNotEmpty()) {
                            { onDoubleTap() }
                        } else {
                            null
                        },
                        onLongPress = if (onLongPressAt != null) {
                            { offset ->
                                val transform = computeFitTransform(imagePainter, boxSize, imageSize)
                                val reference = imageSize
                                    ?: imagePainter?.let {
                                        IntSize(
                                            it.intrinsicSize.width.toInt(),
                                            it.intrinsicSize.height.toInt(),
                                        )
                                    }
                                val fraction = viewOffsetToImageFraction(
                                    offset = offset,
                                    transform = transform,
                                    imageSize = reference,
                                )
                                if (fraction != null) onLongPressAt(fraction.first, fraction.second)
                            }
                        } else {
                            null
                        },
                    )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
                contentAlignment = Alignment.Center,
            ) {
                // Idle artwork. The hint sentence used to be repeated here *and*
                // in StatusBanner (Idle) above; the banner owns the instruction
                // now, so this slot carries the mascot instead of duplicating it.
                //
                // The asset is a real cutout with a transparent backdrop, so it
                // needs no frame to hide a white rectangle. Regenerate it with
                // tools/generate_mascot_asset.py — see
                // docs/knowledge/mascot-ui-asset.md for why that needs rembg.
                Image(
                    painter = painterResource(R.drawable.mascot_glasses_bust),
                    contentDescription = idleMascotA11y,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .testTag("idle_mascot")
                        .size(IdleMascotSize),
                )
            }
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result -> imagePainter = result.painter },
            )
            if (lineBoxes.isNotEmpty()) {
                // imageSize is the stable reference; imagePainter only
                // matters as the fallback when imageSize is null (e.g.
                // shell-profile FakeOcrEngine with no real dims). Once
                // OcrDone lands, the remember key no longer depends on
                // imagePainter, so the transform is set up on the first
                // recomposition after OCR completes — without waiting for
                // Coil to finish loading the bitmap.
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
