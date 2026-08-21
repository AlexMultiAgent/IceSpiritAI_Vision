// app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrFixtureTest.kt
package com.icespiritai.offline.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.TextNormalizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PaddleOcrFixtureTest {

    @Test fun dongjiao_recognizesClaimLine() = runBlocking {
        // androidTest/assets/ 打进的是 test APK,不是 main APK(CLAUDE.md 已踩)。
        // 必须用 Instrumentation 的 context 读 fixture,但写 cacheDir 要用
        // targetContext(appCtx),与现有 PaddleOcrEngineTest 模式一致。
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        // 1) 拷 fixture 到 cacheDir(走 test APK assets,不用 ContentResolver/SDCard)
        val fixtureFile = File(appCtx.cacheDir, "dongjiao.jpg").apply {
            testCtx.assets.open("fixtures/dongjiao_daojia.jpg").use { input ->
                outputStream().use { input.copyTo(it) }
            }
        }
        val uri = Uri.fromFile(fixtureFile)

        // 2) 冷启一次
        val coldStart = System.currentTimeMillis()
        val engine = OcrEngineFactoryLocator.create(appCtx)
        val firstResult = engine.recognize(uri)
        val coldMs = System.currentTimeMillis() - coldStart

        android.util.Log.i("OcrFixture", "cold_ms=$coldMs avgConfidence=${firstResult.avgConfidence}")
        android.util.Log.i("OcrFixture", "fullText=${firstResult.fullText}")

        // 3) 核心断言:claim 关键词至少一个在 fullText(允许 OCR 略有差异)
        val norm = TextNormalizer.forMatching(firstResult.fullText)
        assertTrue(
            "OCR 应检出 claim 关键词(全国 / 技师 / 超9万 / 累计 / 服务 / 1000万 任一)",
            norm.contains("全国") || norm.contains("技师") ||
                norm.contains("超9万") || norm.contains("累计") ||
                norm.contains("服务") || norm.contains("1000万"),
        )

        // 4) 把通过配置的 OCR 输出落成 baseline(供 L2 使用)。
        // 同时 dump 一份到 logcat — connectedDebugAndroidTest 跑完会
        // uninstall test apk,adb pull cacheDir 抢不到时机。
        val baselineJson = buildJsonObject {
            put("avgConfidence", JsonPrimitive(firstResult.avgConfidence.toDouble()))
            put("fullText", JsonPrimitive(firstResult.fullText))
            put("lines", buildJsonArray {
                firstResult.lineBoxes.forEach { line ->
                    add(
                        buildJsonObject {
                            put("text", JsonPrimitive(line.text))
                            put("confidence", JsonPrimitive(line.confidence.toDouble()))
                        },
                    )
                }
            })
        }.toString()
        val baselineFile = File(appCtx.cacheDir, "dongjiao_baseline.json")
        baselineFile.writeText(baselineJson)
        android.util.Log.i("OcrFixture", "baseline saved at ${baselineFile.absolutePath}")
        // 同步把 baseline 内容 dump 到 logcat — connectedDebugAndroidTest 跑完会
        // uninstall test apk,adb pull cacheDir 抢不到时机;logcat 内容只要在
        // 测试启动前开后台捕获,就能可靠 grep 回来。分块避免单行超 logcat 上限。
        val tagged = "OcrFixture baseline:"
        android.util.Log.i("OcrFixture", "$tagged BEGIN len=${baselineJson.length}")
        val chunkSize = 3800
        var i = 0
        while (i < baselineJson.length) {
            val end = (i + chunkSize).coerceAtMost(baselineJson.length)
            android.util.Log.i("OcrFixture", "$tagged $i:$end ${baselineJson.substring(i, end)}")
            i = end
        }
        android.util.Log.i("OcrFixture", "$tagged END")
        Unit  // runBlocking body 必须显式 Unit(CLAUDE.md 踩坑)
    }
}