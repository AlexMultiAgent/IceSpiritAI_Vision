package com.icespiritai.offline

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1 Compose UI smoke test for [IceSpiritVisionActivity].
 *
 * Verifies that the idle screen renders the expected initial affordances:
 * the idle hint text, the pick-image button, and the take-photo button. This
 * does NOT exercise the analysis pipeline — the [IceSpiritVisionViewModel]
 * only constructs the [com.icespiritai.offline.ocr.PaddleOcrEngine] when
 * `startAnalysis()` is called, so the idle screen must be safe to render on
 * a device without OpenCV / ONNX Runtime fully ready.
 */
@RunWith(AndroidJUnit4::class)
class IceSpiritVisionActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<IceSpiritVisionActivity>()

    @Test
    fun app_launches_with_idle_status() {
        composeRule.onNodeWithText("请选择或拍摄一张图片").assertExists()
    }

    @Test
    fun pick_image_button_is_present() {
        composeRule.onNodeWithText("选图").assertExists()
    }

    @Test
    fun take_photo_button_is_present() {
        composeRule.onNodeWithText("拍照").assertExists()
    }
}