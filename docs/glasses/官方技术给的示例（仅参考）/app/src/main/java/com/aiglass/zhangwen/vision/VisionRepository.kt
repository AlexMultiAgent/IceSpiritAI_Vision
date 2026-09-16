package com.aiglass.zhangwen.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.aiglass.zhangwen.config.AliyunVisionConfigStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class VisionResult(
    val success: Boolean,
    val text: String? = null,
    val errorMessage: String? = null
)

class VisionRepository(private val context: Context) {
    private val tag = "GovVision"

    suspend fun recognizeImage(imageFile: File, prompt: String): VisionResult =
        withContext(Dispatchers.IO) {
            val cfg = AliyunVisionConfigStore.load(context)
            if (!cfg.isReady()) {
                return@withContext VisionResult(
                    success = false,
                    errorMessage = "请先在设置中配置阿里云视觉接口地址与模型"
                )
            }
            if (cfg.apiKey.isBlank()) {
                return@withContext VisionResult(
                    success = false,
                    errorMessage = "请先在设置中填写 API Key"
                )
            }
            try {
                val imageBytes = compressImage(imageFile)
                if (imageBytes.isEmpty()) {
                    return@withContext VisionResult(false, errorMessage = "图片为空")
                }
                val mime = when (imageFile.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val dataUrl =
                    "data:$mime;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                val requestJson = JsonObject().apply {
                    addProperty("model", cfg.model.trim().lowercase())
                    add("messages", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            add("content", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("type", "text")
                                    addProperty("text", prompt)
                                })
                                add(JsonObject().apply {
                                    addProperty("type", "image_url")
                                    add("image_url", JsonObject().apply {
                                        addProperty("url", dataUrl)
                                        addProperty("detail", "low")
                                    })
                                })
                            })
                        })
                    })
                    addProperty("temperature", 0.1)
                    addProperty("max_tokens", cfg.maxTokens)
                    addProperty("stream", false)
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(cfg.timeoutSec.toLong(), TimeUnit.SECONDS)
                    .readTimeout(cfg.timeoutSec.toLong(), TimeUnit.SECONDS)
                    .writeTimeout(cfg.timeoutSec.toLong(), TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(cfg.baseUrl)
                    .post(
                        requestJson.toString()
                            .toRequestBody("application/json".toMediaType())
                    )
                    .header("Authorization", "Bearer ${cfg.apiKey}")
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(tag, "视觉调用失败 code=${response.code} body=${body.take(300)}")
                        return@withContext VisionResult(
                            false,
                            errorMessage = "视觉接口失败(${response.code})"
                        )
                    }
                    val text = parseAssistantText(body)
                    if (text.isNullOrBlank()) {
                        return@withContext VisionResult(
                            false,
                            errorMessage = "视觉接口未返回有效内容"
                        )
                    }
                    VisionResult(success = true, text = text.trim())
                }
            } catch (e: Exception) {
                Log.e(tag, "识图异常", e)
                VisionResult(false, errorMessage = e.message ?: "识图异常")
            }
        }

    private fun parseAssistantText(body: String): String? {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val choices = root.getAsJsonArray("choices") ?: return null
            if (choices.size() == 0) return null
            val message = choices[0].asJsonObject.getAsJsonObject("message") ?: return null
            val content = message.get("content") ?: return null
            when {
                content.isJsonPrimitive -> content.asString
                content.isJsonArray -> content.asJsonArray.joinToString("") { el ->
                    when {
                        el.isJsonPrimitive -> el.asString
                        el.isJsonObject && el.asJsonObject.has("text") ->
                            el.asJsonObject.get("text").asString
                        else -> ""
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun compressImage(imageFile: File, maxSidePx: Int = 640, jpegQuality: Int = 55): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (maxSide / sample > maxSidePx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, opts) ?: return byteArrayOf()
        val scaled = scaleBitmap(bitmap, maxSidePx)
        if (scaled !== bitmap) bitmap.recycle()
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        scaled.recycle()
        return baos.toByteArray()
    }

    private fun scaleBitmap(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
