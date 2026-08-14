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
 */
class PaddleOcrEngine(context: Context) : OcrEngine {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var paddleOcr: PaddleOCR? = null

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
            PaddleOCR.create(
                context = appContext,
                config = PaddleOCRConfig(),
                engineConfig = EngineConfig(numThreads = 4),
                detModelAssetPath = "models/det/inference.onnx",
                recModelAssetPath = "models/rec/inference.onnx",
                recConfigAssetPath = "models/rec/inference.yml",
            ).also { paddleOcr = it }
        }

        val bytes = BitmapLoader.bytes(appContext, uri)
            ?: throw OcrEngineUnavailable("Failed to read image stream: $uri")
        val raw = BitmapLoader.downsampledBitmap(bytes)
            ?: throw OcrEngineUnavailable("Failed to decode image: $uri")
        val bitmap = BitmapLoader.applyExifRotation(raw, BitmapLoader.exifRotationDegrees(bytes))

        val runResult = try {
            ocr.recognize(bitmap)
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
            lineBoxes = runResult.results.map { it.toTextLine() },
            avgConfidence = if (runResult.results.isEmpty()) 0f
            else runResult.results.map { it.confidence }.average().toFloat(),
        )
        }
    }

    override suspend fun release() = mutex.withLock {
        paddleOcr?.release()
        paddleOcr = null
    }

    private fun OCRResult.toTextLine(): TextLine =
        TextLine(text = text, box = box.toBoundingRect(), confidence = confidence)

    private fun OCRBox.toBoundingRect(): Rect {
        if (points.isEmpty()) return Rect()
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val left = xs.min().toInt()
        val top = ys.min().toInt()
        val right = xs.max().toInt()
        val bottom = ys.max().toInt()
        return Rect(left, top, right, bottom)
    }
}