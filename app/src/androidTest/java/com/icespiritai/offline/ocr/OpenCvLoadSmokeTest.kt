package com.icespiritai.offline.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * Phase 2 / Task 4 — instrumented smoke test that verifies OpenCV's native
 * libs can be loaded on the test device. Two known failure modes covered:
 *
 *   1. AGP 9 default `useLegacyPackaging = false` leaves `.so` files
 *      compressed inside the APK. `System.loadLibrary` fails to find them
 *      on Android 14/15 when `extractNativeLibs=false`. Fix:
 *      `packaging.jniLibs.useLegacyPackaging = true` in app/build.gradle.kts.
 *
 *   2. `com.quickbirdstudios:opencv:4.5.3` ships native libs built with
 *      NDK r21; the project's locked NDK 28.2.13676358 (2026-08 stack)
 *      bundles a libc++_shared.so that no longer exports the symbol
 *      `__sfp_handle_exceptions` NDK-r21 libopencv_java4.so references.
 *      Fix: switched to `org.opencv:opencv:4.10.0` in gradle/libs.versions.toml.
 *
 * Run:
 *   ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.OpenCvLoadSmokeTest
 */
@RunWith(AndroidJUnit4::class)
class OpenCvLoadSmokeTest {

    @Test
    fun opencv_initLocal_loadsBundledNativeLibs() {
        val ok = OpenCVLoader.initLocal()
        assertTrue(
            "OpenCVLoader.initLocal() returned false. Two known causes: " +
                "(1) AGP 9 default useLegacyPackaging=false leaves .so files " +
                "compressed — verify packaging.jniLibs.useLegacyPackaging=true in " +
                "app/build.gradle.kts; " +
                "(2) opencv-android AAR built with NDK r21 is incompatible with " +
                "project NDK 28.2 — verify org.opencv:opencv:4.10.0 in " +
                "gradle/libs.versions.toml (do NOT use quickbirdstudios:opencv:4.5.3).",
            ok,
        )
    }
}