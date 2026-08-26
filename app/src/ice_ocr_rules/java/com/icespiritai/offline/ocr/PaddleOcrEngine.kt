package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.icespiritai.offline.domain.OcrEngineUnavailable
import com.icespiritai.offline.domain.OcrFailed
import com.icespiritai.offline.domain.OcrResult
import com.icespiritai.offline.domain.TextLine
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRError
import com.paddle.ocr.model.OCRResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

/**
 * Production [OcrEngine] wrapping the official PaddleOCR SDK (v3.7.0).
 *
 * Native resources (ONNX Runtime sessions, OpenCV Mat arenas) are held by the
 * underlying [PaddleOCR] instance. We lazy-init on first [recognize] and tear
 * down via [release] — the [Mutex] guards concurrent first-touch so two callers
 * don't both pay the cold-load cost.
 *
 * Error mapping:
 *   - Config / model load failures (`ModelLoadFailed`, `ModelNotFound`,
 *     `ConfigParseFailed`) → [OcrEngineUnavailable]: the device cannot perform
 *     OCR until the asset / build configuration is fixed.
 *   - Runtime / image failures (`InvalidImage`, `DecodeError`,
 *     `InferenceFailed`, generic `Exception`) → [OcrFailed]: the call itself
 *     is bad, retrying may help.
 *
 * @param recBatchSize SDK rec batch size — number of detected text lines
 *   packed per rec forward pass. 6 is the v0.1.11 default (a Phase 2
 *   instrumented-test sweep on a Huawei nova 6 ARM64 / SDK 35 found that 6
 *   amortizes preprocess overhead on large images and stays within the
 *   1-3 s per-image SLA; smaller images waste cycles waiting for the batch
 *   to fill — see [docs/smoke/2026-08-20-icevision-v0.1.12-real-device.md]).
 *   Change requires engine recreation (the SDK reads this at
 *   `PaddleOCR.create` time, not per call), so each distinct value spins up
 *   a separate `PaddleOcrEngine` instance.
 */
class PaddleOcrEngine(
    context: Context,
    private val recBatchSize: Int = DEFAULT_REC_BATCH_SIZE,
) : OcrEngine {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var paddleOcr: PaddleOCR? = null

    companion object {
        /** v0.1.11 / v0.1.12 default. Validated on Huawei nova 6 ARM64. */
        const val DEFAULT_REC_BATCH_SIZE = 6
    }

    /**
     * OpenCV's native libs (used internally by PaddleOCR for `Bitmap → Mat`)
     * must be loaded once per process before any OpenCV JNI call. We attempt
     * that load in the engine's class initializer and surface failure as a
     * captured flag rather than throwing — the throw happens lazily inside
     * [recognize] so callers see a typed [OcrEngineUnavailable] instead of an
     * exception bubbled up from a constructor.
     *
     * Phase 2 / Task 4 hotfix: switched to `org.opencv:opencv:4.10.0` (the
     * previous `com.quickbirdstudios:opencv:4.5.3` was built with NDK r21,
     * which references removed libc++ ABI symbols like `__sfp_handle_exceptions`
     * — incompatible with our locked NDK 28.2.13676358). `initDebug()` is
     * deprecated in 4.10.0; `initLocal()` is the recommended entry point for
     * bundled native libs (no OpenCV Manager dependency). Safe to call from
     * any thread — `initLocal()` is idempotent and registers process-wide
     * JNI bindings.
     */
    private val openCvLoaded: Boolean = OpenCVLoader.initLocal()

    override suspend fun recognize(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ocr = paddleOcr ?: run {
                if (!openCvLoaded) {
                    throw OcrEngineUnavailable(
                        "OpenCV native libs failed to load — check that the opencv-android " +
                            "AAR's native libs are bundled in the APK (arm64-v8a)"
                    )
                }
                val created = try {
                    PaddleOCR.create(
                        context = appContext,
                        // v6_small model-card-aligned config (PaddleOCR SDK v3.7.0
                        // default PaddleOCRConfig() is 64/min/thresh0.3/box0.6/unclip1.5/
                        // recScoreThresh 0.0/recBatchSize 1 — silently undercuts v6
                        // recall and inflates rec noise feeding the rule engine).
                        // Det params (960/max/0.2/0.45/1.4) match PP-OCRv6_small_det
                        // inference.yml PostProcess. recScoreThresh=0.5 filters low-
                        // confidence noise before the rule scan (avg v6 score 0.882
                        // on 4-image benchmark, so 0.5 keeps all real text but drops
                        // garbage). recBatchSize=6 amortizes rec preprocess; 1 vs 6
                        // speedup needs real-device confirmation (logged for Phase 2).
                        config = PaddleOCRConfig(
                            detLimitSideLen = 960,
                            detLimitType = "max",
                            detThresh = 0.2f,
                            detBoxThresh = 0.45f,
                            detUnclipRatio = 1.4f,
                            recScoreThresh = 0.5f,
                            recBatchSize = recBatchSize,
                        ),
                        engineConfig = EngineConfig(numThreads = 4),
                        detModelAssetPath = "models/det/inference.onnx",
                        recModelAssetPath = "models/rec/inference.onnx",
                        recConfigAssetPath = "models/rec/inference.yml",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OCRError.ModelLoadFailed) {
                    throw OcrEngineUnavailable("OCR model load failed: ${e.message}", e)
                } catch (e: OCRError.ModelNotFound) {
                    throw OcrEngineUnavailable("OCR model missing in assets: ${e.message}", e)
                } catch (e: OCRError.ConfigParseFailed) {
                    throw OcrEngineUnavailable("OCR config parse failed: ${e.message}", e)
                } catch (e: Exception) {
                    // Engine construction failure is a packaging/device problem,
                    // not a bad image: retrying with another photo won't help.
                    throw OcrEngineUnavailable("OCR engine init failed: ${e.message}", e)
                }
                created.also { paddleOcr = it }
            }

            val bytes = BitmapLoader.bytes(appContext, uri)
                ?: throw OcrFailed("Failed to read image stream: $uri")
            // BitmapFactory.decodeByteArray already applies EXIF orientation
            // on API 24+ (minSdk=26 here), so the bitmap is in display
            // orientation. Manually rotating again with
            // [BitmapLoader.applyExifRotation] would double-rotate and put
            // OCR boxes in a coordinate space that doesn't match Coil's
            // painter.intrinsicSize — causing HighlightOverlay rects to land
            // off-text on any non-EXIF-1 photo (verified on the 8-hit corn
            // advertisement fixture, boxes drifted into the right margin
            // and the OCR-text panel). The Phase 2 design doc said the
            // opposite, but that was wrong for API 24+; the EXIF helpers in
            // BitmapLoader are kept as utilities but no longer wired here.
            val loaded = BitmapLoader.downsampledBitmapWithScale(bytes)
                ?: throw OcrFailed("Failed to decode image: $uri")
            val bitmap = loaded.bitmap

            val runResult = try {
                ocr.recognize(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OCRError) {
                throw when (e) {
                    is OCRError.ModelLoadFailed ->
                        OcrEngineUnavailable("OCR model load failed: ${e.message}", e)
                    is OCRError.ModelNotFound ->
                        OcrEngineUnavailable("OCR model missing in assets: ${e.message}", e)
                    is OCRError.ConfigParseFailed ->
                        OcrEngineUnavailable("OCR config parse failed: ${e.message}", e)
                    is OCRError.InvalidImage ->
                        OcrFailed("Invalid image", e)
                    is OCRError.DecodeError ->
                        OcrFailed("OCR decode failed: ${e.message}", e)
                    is OCRError.InferenceFailed ->
                        OcrFailed("OCR inference failed: ${e.message}", e)
                }
            } catch (e: Exception) {
                throw OcrFailed("OCR runtime error: ${e.message}", e)
            }

            OcrResult(
                fullText = runResult.results.joinToString("\n") { it.text },
                lineBoxes = runResult.results.map { it.toTextLine(loaded.sampleSize) },
                avgConfidence = if (runResult.results.isEmpty()) 0f
                else runResult.results.map { it.confidence }.average().toFloat(),
                // Display-oriented dimensions of the FULL bitmap PaddleOCR
                // actually saw. [loaded.bitmap] is post-EXIF-rotation on
                // API 24+ (minSdk=26), so bitmap.width/height are already
                // in the same coordinate space as the boxes above (which
                // were multiplied by loaded.sampleSize to undo the
                // power-of-two downsample). ImagePreview consumes these
                // as the reference dims for HighlightOverlay's transform
                // — NOT painter.intrinsicSize, which reflects Coil's
                // layout-size downsampled bitmap and would put boxes in
                // the wrong coordinate space.
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            )
        }
    }

    override suspend fun release() = mutex.withLock {
        paddleOcr?.release()
        paddleOcr = null
    }

    private fun OCRResult.toTextLine(scale: Int): TextLine =
        TextLine(text = text, box = box.toBoundingRect(scale), confidence = confidence)

    private fun OCRBox.toBoundingRect(scale: Int): Rect {
        if (points.isEmpty()) return Rect()
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val left = xs.min().toInt() * scale
        val top = ys.min().toInt() * scale
        val right = xs.max().toInt() * scale
        val bottom = ys.max().toInt() * scale
        return Rect(left, top, right, bottom)
    }
}
