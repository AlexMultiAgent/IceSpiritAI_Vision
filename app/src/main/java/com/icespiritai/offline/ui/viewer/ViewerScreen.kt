package com.icespiritai.offline.ui.viewer

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.icespiritai.offline.domain.TextLine

/**
 * Full-screen route for the image viewer (Routes.VIEWER).
 *
 * Top half is the original photo rendered through Telephoto
 * [me.saket.telephoto.zoomable.coil.ZoomableAsyncImage] (pinch / pan /
 * double-tap-zoom). Bottom half is a per-line scrollable OCR text list.
 *
 * When [imageUri] is null (defensive edge — usually happens when
 * navigating to the route before HomeScreen has a pending capture / pick
 * URI), [ViewerEmpty] is shown centered in the body instead.
 *
 * @param imageUri the analyzed image's URI (same as HomeScreen.pendingUri).
 * @param lineBoxes per-line OCR output; drives the text list rows.
 * @param hitsCount number of rule hits; surfaces in the text-list header.
 * @param onBack invoked when the user taps the back arrow (pop nav stack).
 */
@Composable
fun ViewerScreen(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hitsCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ViewerTopBar(onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (imageUri == null) {
                ViewerEmpty(modifier = Modifier.weight(1f))
            } else {
                ViewerImage(
                    imageUri = imageUri,
                    modifier = Modifier.weight(1f),
                )
                ViewerTextList(
                    lineBoxes = lineBoxes,
                    hitsCount = hitsCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}