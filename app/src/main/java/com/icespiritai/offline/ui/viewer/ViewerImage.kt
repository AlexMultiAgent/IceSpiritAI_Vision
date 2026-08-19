package com.icespiritai.offline.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

/**
 * Top half of [ViewerScreen]. Loads [imageUri] through Coil into a
 * Telephoto `ZoomableAsyncImage` so the user can pinch-zoom, drag-pan, and
 * double-tap-toggle the original photo.
 *
 * Telephoto's ZoomableAsyncImage replaces the static `AsyncImage` we use in
 * `HomeScreen.ImagePreview`. It internally manages:
 *  - pinch zoom (within sensible min/max zoom range)
 *  - single-finger drag pan (only when zoomed in)
 *  - double-tap to toggle between fit-screen and 2x zoom
 *  - fling & inertia
 *
 * Telephoto's gesture pipeline lives in native Compose pointer input — it
 * cannot be exercised by Robolectric unit tests. Manual on-device verification
 * is the only signal here; see docs/superpowers/specs/2026-08-19-icevision-image-viewer-design.md
 * §6 acceptance checklist.
 *
 * @param imageUri URI of the analyzed image (from FileProvider capture or
 *   gallery picker). The same URI used by `ImagePreview`.
 */
@Composable
fun ViewerImage(
    imageUri: Uri?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
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
            ZoomableAsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.viewer_image_cd),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}