package com.icespiritai.buildhelpers

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The guard is the only gate that distinguishes a shippable APK from the
 * default `shell` profile — signing, cert-pin, JSON and versionCode all pass
 * for both (2026-09-17: a 38 MB model-less APK reached the live channel).
 */
class ReleaseArtifactGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun apkWith(vararg entries: String): File {
        val f = tmp.newFile("app-release.apk")
        ZipOutputStream(f.outputStream()).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(0x50, 0x4B))
                zip.closeEntry()
            }
        }
        return f
    }

    @Test
    fun ocrProfileApkPasses() {
        val apk = apkWith(
            "assets/dexopt/baseline.prof",
            "assets/models/det/inference.onnx",
            "assets/models/det/inference.yml",
            "assets/models/rec/inference.onnx",
            "assets/models/rec/inference.yml",
            "assets/user-changelog.md",
        )
        val result = ReleaseArtifactGuard.inspect(apk)
        assertTrue(result.ok)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun shellProfileApkIsRejectedWithTheExactMissingAssets() {
        // Shape of the 2026-09-17 bad publish: TTS assets only, no det/rec.
        val apk = apkWith(
            "assets/dexopt/baseline.prof",
            "assets/models/tts/zh/date.fst",
            "assets/user-changelog.md",
        )
        val result = ReleaseArtifactGuard.inspect(apk)
        assertFalse(result.ok)
        assertTrue(result.missing.containsAll(ReleaseArtifactGuard.REQUIRED_ENTRIES))
        val message = result.explain(apk)
        assertTrue("message should name the fix", message.contains("modelProfile=ice_ocr_rules"))
    }

    @Test
    fun partiallyBundledProfileIsAlsoRejected() {
        // Detector present, recogniser missing — still unshippable.
        val apk = apkWith(
            "assets/models/det/inference.onnx",
            "assets/models/det/inference.yml",
        )
        val result = ReleaseArtifactGuard.inspect(apk)
        assertFalse(result.ok)
        assertTrue(result.missing.any { it.contains("rec/") })
    }

    @Test
    fun nonApkInputFailsFast() {
        val notZip = tmp.newFile("app-release.apk")
        notZip.writeText("not a zip")
        var threw = false
        try {
            ReleaseArtifactGuard.inspect(notZip)
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("a corrupt APK must fail the release, not pass it", threw)
    }
}
