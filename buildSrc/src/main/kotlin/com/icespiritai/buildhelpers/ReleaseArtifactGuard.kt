package com.icespiritai.buildhelpers

import java.io.File
import java.util.zip.ZipFile

/**
 * Pre-publish sanity check: **does this APK actually contain the OCR model?**
 *
 * Why this exists (2026-09-17 incident): `modelProfile` defaults to `shell`,
 * and `shell` bundles *no* vision model and *no* native OCR runtime
 * (`app/build.gradle.kts` header says so explicitly). The release skill's
 * documented command was a bare `assembleRelease`, so a 38 MB `shell` APK —
 * one whose OCR can never run — was published to the live `latest` tag as
 * v0.5.1 and had to be replaced within minutes.
 *
 * A missing model is invisible in every other gate: signing is fine, the
 * cert-pin passes, the JSON is consistent with the APK, and `versionCode`
 * matches. Only the archive contents tell the two worlds apart, so that is
 * what this checks.
 *
 * Intentional small-payload publishes (e.g. exercising the in-app updater
 * with a tiny APK) stay possible via the explicit
 * `-PallowShellRelease=true` opt-out at the call site — loud, but not silent.
 */
object ReleaseArtifactGuard {

    /**
     * Assets that only the real OCR profile produces. Both must be present:
     * the detector is what finds text boxes, the recogniser is what reads
     * them, and shipping one without the other fails just as hard.
     */
    val REQUIRED_ENTRIES: List<String> = listOf(
        "assets/models/det/inference.onnx",
        "assets/models/det/inference.yml",
        "assets/models/rec/inference.onnx",
        "assets/models/rec/inference.yml",
    )

    /** Result of [inspect]; [ok] false means "do not publish this APK". */
    data class Inspection(
        val ok: Boolean,
        val missing: List<String>,
        val entryCount: Int,
    ) {
        /** Human-readable explanation for the Gradle failure message. */
        fun explain(apk: File): String = buildString {
            append("APK ${apk.name} (${apk.length()} B) is missing ")
            append(missing.size)
            append(" required OCR asset(s): ")
            append(missing.joinToString(", "))
            append(". ")
            append("This is almost always a build without ")
            append("-PmodelProfile=ice_ocr_rules (the default `shell` profile bundles ")
            append("no model and no native OCR runtime, ~38 MB instead of ~75 MB). ")
            append("Re-run: ./gradlew assembleRelease -PmodelProfile=ice_ocr_rules")
        }
    }

    /**
     * Inspect [apk]'s zip entries. Never throws for a missing entry — the
     * caller decides (see [Inspection.explain]).
     */
    fun inspect(apk: File): Inspection {
        require(apk.isFile) { "inspect: ${apk.absolutePath} is not a file" }
        val names = ZipFile(apk).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        val missing = REQUIRED_ENTRIES.filterNot { it in names }
        return Inspection(ok = missing.isEmpty(), missing = missing, entryCount = names.size)
    }
}
