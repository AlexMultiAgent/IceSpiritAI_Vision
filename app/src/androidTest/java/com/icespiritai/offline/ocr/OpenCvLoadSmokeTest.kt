package com.icespiritai.offline.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * Phase 2 / Task 4 — instrumented smoke test that verifies OpenCV's native
 * libs can be loaded on the test device. Targets `useLegacyPackaging = true`
 * fix (Phase 2 / Task 4): AGP 9 defaults to `useLegacyPackaging = false`
 * which leaves `.so` files compressed inside the APK. `System.loadLibrary`
 * then fails to find them on Android 14/15 when `extractNativeLibs=false`.
 *
 * Run:
 *   ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.OpenCvLoadSmokeTest
 */
@RunWith(AndroidJUnit4::class)
class OpenCvLoadSmokeTest {

    @Test
    fun opencv_initDebug_loadsBundledNativeLibs() {
        val ok = OpenCVLoader.initDebug()
        assertTrue(
            "OpenCVLoader.initDebug() returned false. Likely cause: " +
                "AGP default useLegacyPackaging=false leaves .so files compressed " +
                "in the APK. Set packaging.jniLibs.useLegacyPackaging = true in " +
                "app/build.gradle.kts.",
            ok,
        )
    }
}