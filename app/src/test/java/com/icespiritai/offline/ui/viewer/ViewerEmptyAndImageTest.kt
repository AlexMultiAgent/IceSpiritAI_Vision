package com.icespiritai.offline.ui.viewer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the empty + image load-error branches of the
 * Viewer.
 *
 * Pins:
 *  - [ViewerEmpty] renders its empty-state hint text
 *  - [ViewerImage] with `imageUri = null` renders the
 *    "图片加载失败" load-error text (this is the branch hit when
 *    the user pops into Viewer without a URI on hand)
 *
 * The happy branch of [ViewerImage] (non-null URI → ZoomableAsyncImage)
 * is NOT exercised here — ZoomableAsyncImage's gesture pipeline lives
 * in native Compose pointer input and cannot be driven by Robolectric.
 * Manual on-device verification is the only signal for that path (see
 * the design doc reference in `ViewerImage.kt`).
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerEmptyAndImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `ViewerEmpty renders the empty-state hint`() {
        composeRule.setContent {
            ViewerEmpty()
        }
        composeRule.onNodeWithText("无可查看结果,请先拍照或选图识别").assertExists()
    }

    @Test
    fun `ViewerImage with null URI renders the load-error text`() {
        composeRule.setContent {
            ViewerImage(imageUri = null, lineBoxes = emptyList(), hits = emptyList(), imageSize = null)
        }
        composeRule.onNodeWithText("图片加载失败").assertExists()
    }
}
