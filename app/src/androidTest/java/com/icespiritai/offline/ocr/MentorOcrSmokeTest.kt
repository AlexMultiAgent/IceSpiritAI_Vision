// app/src/androidTest/java/com/icespiritai/offline/ocr/MentorOcrSmokeTest.kt
package com.icespiritai.offline.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Smoke test for PaddleOcrEngine on 4 mentor ad-signage images. Guards against
 * OCR threshold / post-processing changes that over- or under-tune recall on
 * the regression set. Runs on real device via connectedDebugAndroidTest.
 *
 * The 5th mentor image (11_2011 / 东郊到家) is intentionally absent from this
 * set: it already has a dedicated fixture at `fixtures/dongjiao_daojia.jpg`
 * exercised by `PaddleOcrFixtureTest` (Task 5) and the rule-level regression
 * (`AdSignageMentorFiveImageRegressionTest`). Keeping it out of this fixture
 * avoids duplicate coverage and a misleading "5 of 5" naming gap.
 */
@RunWith(AndroidJUnit4::class)
class MentorOcrSmokeTest {

    /**
     * Mentor fixture basenames — must match the files copied into
     * `app/src/androidTest/assets/fixtures/mentor/`.
     * The dongjiao image (11_2011) lives at `fixtures/dongjiao_daojia.jpg`
     * and is covered by [PaddleOcrFixtureTest] instead.
     */
    private val fixtureNames = listOf(
        "mentor_1_5_2011.jpg",
        "mentor_2_6_2011.jpg",
        "mentor_3_9_2011.jpg",
        "mentor_4_20.jpg",
    )

    @Test fun allMentorFixtures_ocrReturnsValidOutput() = runBlocking {
        // androidTest/assets/ is packaged into the test APK, not the main APK
        // (CLAUDE.md known pitfall). Use the Instrumentation context for
        // `assets.open(...)` and targetContext for cacheDir + engine creation,
        // matching the pattern in `PaddleOcrFixtureTest` (Task 5).
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val engine = OcrEngineFactoryLocator.create(appCtx)

        for (name in fixtureNames) {
            val fixtureFile = File(appCtx.cacheDir, name).apply {
                testCtx.assets.open("fixtures/mentor/$name").use { input ->
                    outputStream().use { input.copyTo(it) }
                }
            }
            val result = engine.recognize(Uri.fromFile(fixtureFile))
            android.util.Log.i(
                "MentorSmoke",
                "$name lines=${result.lineBoxes.size} avgConf=${result.avgConfidence}",
            )
            assertTrue("$name 应至少 1 行", result.lineBoxes.isNotEmpty())
            assertTrue("$name avgConfidence ≥ 0.5", result.avgConfidence >= 0.5f)
            result.lineBoxes.forEach { line ->
                assertTrue("$name 行 confidence ≥ 0.3", line.confidence >= 0.3f)
                assertTrue("$name 行 text 非空", line.text.isNotBlank())
            }
        }
        Unit  // runBlocking body 必须显式 Unit(CLAUDE.md 踩坑)
    }
}
