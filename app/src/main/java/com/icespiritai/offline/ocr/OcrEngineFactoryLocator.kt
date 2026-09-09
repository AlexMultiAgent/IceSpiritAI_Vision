package com.icespiritai.offline.ocr

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.icespiritai.offline.BuildConfig
import java.util.ServiceLoader

object OcrEngineFactoryLocator {
    fun create(context: Context): OcrEngine {
        val factory = ServiceLoader.load(OcrEngineFactory::class.java)
            .firstOrNull()
            ?: error(
                "No OcrEngineFactory on classpath. Check " +
                    "src/{shell,ice_ocr_rules}/resources/META-INF/services/.",
            )

        // Defensive: the factory FQN must match the active MODEL_PROFILE.
        // Profiles without an explicit name marker (shell ships
        // FakeOcrEngineFactory which has no "shell" substring) are allowed
        // by mapping each profile to a set of acceptable factory substrings.
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

        return factory.create(context)
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
     */
    @VisibleForTesting
    internal fun markersFor(profile: String): List<String> = when (profile) {
        "shell" -> listOf("Fake")
        "ice_ocr_rules" -> listOf("Paddle", "Pp", "Onnx")
        "ice_vision" -> listOf("Fake", "Vision")
        else -> listOf(profile)
    }
}
