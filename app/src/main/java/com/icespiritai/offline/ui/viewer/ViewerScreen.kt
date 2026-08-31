package com.icespiritai.offline.ui.viewer

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine

/**
 * Full-screen route for the image viewer (Routes.VIEWER).
 *
 * Top half is the original photo rendered through Telephoto
 * [me.saket.telephoto.zoomable.coil.ZoomableAsyncImage] (pinch / pan /
 * double-tap-zoom) **with a `HighlightOverlay` on top** so the user can
 * see the per-rule boxes (red = Violation, amber = Warning, blue = Info)
 * overlaid on the full-resolution image. Bottom half is a per-line
 * scrollable OCR text list.
 *
 * Phase 3.5 (2026-08-31): the Viewer now passes [hits] (not just the
 * count) and [imageSize] (the OCR engine's reference dims) to the image
 * composable so the highlight boxes can be drawn at the right scale on
 * the fullscreen image, not just the home preview.
 *
 * When [imageUri] is null (defensive edge — usually happens when
 * navigating to the route before HomeScreen has a pending capture / pick
 * URI), [ViewerEmpty] is shown centered in the body instead.
 *
 * @param imageUri the analyzed image's URI (same as HomeScreen.pendingUri).
 * @param lineBoxes per-line OCR output; drives the text list rows.
 * @param hits rule hits; drives the highlight overlay colors per line.
 * @param hitsCount number of rule hits; surfaces in the text-list header.
 * @param imageSize display-oriented dims of the FULL bitmap the OCR
 *   engine processed — feeds the HighlightOverlay transform.
 * @param onBack invoked when the user taps the back arrow (pop nav stack).
 */
@Composable
fun ViewerScreen(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    hitsCount: Int,
    imageSize: IntSize?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ViewerTopBar(onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).animateContentSize()) {
            if (imageUri == null) {
                ViewerEmpty(modifier = Modifier.weight(1f))
            } else {
                ViewerImage(
                    imageUri = imageUri,
                    lineBoxes = lineBoxes,
                    hits = hits,
                    imageSize = imageSize,
                    modifier = Modifier.weight(1f),
                )
                ViewerTextList(
                    lineBoxes = lineBoxes,
                    hits = hits,
                    hitsCount = hitsCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}