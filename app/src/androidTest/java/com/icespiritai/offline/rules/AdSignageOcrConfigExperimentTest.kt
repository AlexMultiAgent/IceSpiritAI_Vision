package com.icespiritai.offline.rules

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.ocr.PaddleOcrEngine
import com.paddle.ocr.PaddleOCRConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * OCR PaddleOCRConfig A/B 实验 — 14 张样本(9 OCR 错图 + 5 baseline FULL 对照)
 * × 2 config (current vs phase2 candidate)。
 *
 * Phase 2 起因:Phase 1 扩词后 22 张仍 MISS,9 张 OCR 端错图。Workflow 诊断:
 *   - #19 #32 #33 (under-OCR): detLimitSideLen 960→1280 + detLimitType "max"→"min"
 *     (1755x19893 长图 max 缩到 960 → 字符消失)
 *   - #37 #38 #40 #53 #35 (overlay / sidebar): detThresh 0.2→0.3 + detBoxThresh 0.45→0.55
 *     + detUnclipRatio 1.4→1.6
 *
 * 测试用 PaddleOcrEngine(appCtx, recBatchSize=6, configOverride=cfg) 接口
 * (Phase 2 新加的第三个构造参数)。
 *
 * 输出: logcat TAG=OcrConfigExp,DIFF 行,A vs B chars/hits delta
 */
@RunWith(AndroidJUnit4::class)
class AdSignageOcrConfigExperimentTest {

    private val tag = "OcrConfigExp"

    private val sampleImages = listOf(
        // 9 OCR 错图(Phase 1 后仍 MISS)— slot 编号 + fixture rename 后实际 APK 内的文件名
        "09_榛蘑_可增强肌体免疫力益智_食用菌.png",
        "19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg",
        "32_白桦树汁植物饮料_提高免疫力儿童和老人.png",
        "33_公考培训宋尚案老师_某省直机关在职领导_身份.png",
        "35_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png",
        "37_优普派阿苯达唑片_无效包退800万粉丝_兽药.png",
        "38_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png",
        "40_体重管理减肥产品_两月减重31斤_减肥承诺.png",
        "53_北大荒椴树雪蜜_提高人体免疫力_保健食品.png",
        // 5 baseline FULL 对照(期望 config B 不劣化)
        "07_保健食品8大优势_提高免疫力消炎止痛.png",
        "18_白酒电商页_闻香入口天然桦树清香.png",
        "20_黑尊牛安格斯牛肉礼盒_送领导客户清真.png",
        "22_坤丰绿旋风2号辣椒种子_高产南北方栽培_种子.png",
        "41_肺肽片保健食品_清肺排毒提升肺功能_食品.png",
    )

    private val configs = listOf(
        "A_current" to PaddleOCRConfig(
            detLimitSideLen = 960,
            detLimitType = "max",
            detThresh = 0.2f,
            detBoxThresh = 0.45f,
            detUnclipRatio = 1.4f,
            recScoreThresh = 0.5f,
            recBatchSize = 6,
        ),
        "B_phase2" to PaddleOCRConfig(
            detLimitSideLen = 1280,
            detLimitType = "min",
            detThresh = 0.3f,
            detBoxThresh = 0.5f,
            detUnclipRatio = 1.6f,
            recScoreThresh = 0.5f,
            recBatchSize = 6,
        ),
    )

    @Test
    fun ocrConfigA_bExperiment() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initLocal(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        val rules = AdSignageRuleLoader(appCtx).load()
        val matcher = AdSignageRuleMatcher(rules)

        Log.i(tag, "=== START: ${sampleImages.size} images × ${configs.size} configs ===")

        // Map<file, Map<configName, Measurement>>
        val measurements = mutableMapOf<String, MutableMap<String, Int>>()

        for ((configName, cfg) in configs) {
            Log.i(tag, "--- Config: $configName ---")
            val engine = PaddleOcrEngine(appCtx, recBatchSize = 6, configOverride = cfg)

            for ((i, file) in sampleImages.withIndex()) {
                val uri = stage(appCtx, testCtx, file, "oce_${configName}_$file")
                val start = System.nanoTime()
                val res = engine.recognize(uri)
                val ms = (System.nanoTime() - start) / 1_000_000L
                val hits = matcher.scan(res.fullText)
                val first = (i == 0)
                Log.i(
                    tag, "[$configName ${if (first) "COLD" else "WARM"}] $file ms=$ms " +
                        "lines=${res.lineBoxes.size} avg_conf=${"%.3f".format(res.avgConfidence)} " +
                        "text_chars=${res.fullText.length} hits=${hits.size} " +
                        "hit_ids=${hits.joinToString(",") { it.ruleId }}"
                )
                measurements.getOrPut(file) { mutableMapOf() }[configName] = res.fullText.length
            }
            engine.release()
        }

        // Diff per file
        Log.i(tag, "=== A vs B DIFF ===")
        var aWins = 0
        var bWins = 0
        var ties = 0
        for (file in sampleImages) {
            val aChars = measurements[file]?.get("A_current") ?: 0
            val bChars = measurements[file]?.get("B_phase2") ?: 0
            val delta = bChars - aChars
            val verdict = when {
                delta >= 20 -> {
                    bWins++
                    "B_WINS(+${delta})"
                }
                delta <= -20 -> {
                    aWins++
                    "A_WINS(${delta})"
                }
                else -> {
                    ties++
                    "TIE(Δ$delta)"
                }
            }
            Log.i(tag, "DIFF $file chars: A=$aChars B=$bChars → $verdict")
        }
        Log.i(tag, "=== SUMMARY: A wins=$aWins B wins=$bWins ties=$ties ===")
        Unit
    }

    private fun stage(appCtx: Context, testCtx: Context, filename: String, stageFilename: String): Uri {
        val cacheFile = File(appCtx.cacheDir, stageFilename)
        if (!cacheFile.exists()) {
            testCtx.assets.open("fixtures/audit66/$filename").use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Uri.fromFile(cacheFile)
    }
}