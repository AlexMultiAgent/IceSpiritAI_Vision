package com.icespiritai.offline.ocr

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.icespiritai.offline.BuildConfig
import java.util.ServiceLoader

object OcrEngineFactoryLocator {
    /**
     * Resolve the [OcrEngineFactory] for the active model profile and create
     * an [OcrEngine]. The factory FQN must match one of the markers returned
     * by [markersFor] for the active profile — otherwise this throws, which
     * surfaces a profile / factory wiring mismatch at first launch instead of
     * silently falling through to a wrong engine.
     */
    fun create(context: Context): OcrEngine {
        val factory = resolveFactory()
        assertProfileMatchesActive(factory)
        return factory.create(context)
    }

    /**
     * Test seam: bypass the ServiceLoader roundtrip so unit tests can drive
     * the OCR pipeline without depending on `META-INF/services/` being on the
     * classpath. The profile-marker check still runs so a test that injects
     * a factory under the wrong profile fails the same way production does.
     */
    @VisibleForTesting
    fun create(context: Context, factory: OcrEngineFactory): OcrEngine {
        assertProfileMatchesActive(factory)
        return factory.create(context)
    }

    private fun resolveFactory(): OcrEngineFactory =
        ServiceLoader.load(OcrEngineFactory::class.java).firstOrNull()
            ?: error(
                "No OcrEngineFactory on classpath. Check " +
                    "src/{shell,ice_ocr_rules}/resources/META-INF/services/.",
            )

    private fun assertProfileMatchesActive(factory: OcrEngineFactory) {
        val expectedProfile = BuildConfig.MODEL_PROFILE
        val actualClass = factory::class.qualifiedName.orEmpty()
        val expectedMarkers = markersFor(expectedProfile)
        val matches = expectedMarkers.any { marker ->
            actualClass.contains(marker, ignoreCase = true)
        }
        if (!matches) {
            error(
                "OcrEngineFactory '$actualClass' does not match model profile " +
                    "'$expectedProfile' (expected markers: $expectedMarkers). " +
                    "Re-check buildProfileServicesJar in " +
                    "app/prepare-ocr-rules.gradle.kts.",
            )
        }
    }

    /**
     * Acceptable factory FQN substrings per profile. ice_vision falls back to
     * Fake until its real factory is implemented (per audit P2-3).
     *
     * Bug 8 fix (2026-09-10): the previous single-marker form
     * (`listOf("Paddle")`) was fragile under class renames — if anyone
     * ever refactors `PaddleOcrEngineFactory` to `OnnxPaddleOcrEngineFactory`
     * or `PpOcrEngineFactory`, the substring check would silently fail and
     * `create()` would `error("does not match model profile …")` at first
     * launch. The marker set now covers reasonable Paddle / ONNX /
     * short-form `Pp` renames, so the profile guard survives typical
     * refactors. Marked `@VisibleForTesting` so the contract is pinned at
     * the unit-test layer (see `OcrEngineFactoryLocatorTest`).
     *
     * The shell profile accepts both `Fake` (test-only) and `Stub` (the
     * runtime shell stub) so a test injecting either factory passes the
     * guard. In production the shell APK only has `ShellStubOcrEngineFactory`
     * on its classpath — `Fake` matches but the class isn't there to be
     * loaded, so the indirection is harmless.
     */
    @VisibleForTesting
    internal fun markersFor(profile: String): List<String> = when (profile) {
        "shell" -> listOf("Fake", "Stub")
        "ice_ocr_rules" -> listOf("Paddle", "Pp", "Onnx")
        "ice_vision" -> listOf("Fake", "Vision")
        else -> listOf(profile)
    }
}
