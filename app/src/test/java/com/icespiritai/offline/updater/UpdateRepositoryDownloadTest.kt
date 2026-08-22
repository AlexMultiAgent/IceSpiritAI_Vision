package com.icespiritai.offline.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Cert-pin gate tests. The byte-stream download path moved to
 * [com.icespiritai.offline.updater.ApkDownloader] (covered by
 * `ApkDownloaderTest.kt`); the FGS owns the lifecycle and the Repository
 * only fires the Intent. So this file keeps only the two tests that exercise
 * [UpdateRepository.verifySignatureForDownload], which still lives on the
 * Repository and is the public surface UI tests poke at.
 */
class UpdateRepositoryDownloadTest {

    private val apkUrl = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespritai-vision.apk"
    private val info = AppVersionInfo(
        versionCode = 2, versionName = "0.2.0",
        apkUrl = apkUrl, apkSize = 1024L,
        apkSha256 = "a".repeat(64), changelog = "",
    )

    @Test
    fun signatureGate_skipsWhenSignerCertSha256IsEmpty() {
        // Backward compat: vision-latest.json published before Wave 1 Task 1.2
        // carries no signerCertSha256; gate must NOT block such updates.
        val tmp = Files.createTempFile("icespirit-notapk", ".bin").toFile()
        tmp.writeText("not a real apk, but the verifier must not even be invoked")
        val infoNoCert = info.copy(signerCertSha256 = "")
        try {
            val result = UpdateRepository.verifySignatureForDownload(infoNoCert, tmp)
            assertNull(
                "empty signerCertSha256 must skip the cert-pin gate (download → ReadyToInstall)",
                result,
            )
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun signatureGate_flagsMismatchWhenActualIsNullAndExpectedNonEmpty() {
        // File is plain text, not a JAR/ZIP — ApkSignatureVerifier returns null.
        // Gate must surface SignatureMismatch(actual=null, expected=...) and NOT fall
        // through to ReadyToInstall.
        val tmp = Files.createTempFile("icespirit-notapk2", ".bin").toFile()
        tmp.writeText("plain text — JarFile will reject this and return null")
        val expectedHex = "deadbeef".repeat(8) // 64 chars
        val infoPinned = info.copy(signerCertSha256 = expectedHex)
        try {
            val result = UpdateRepository.verifySignatureForDownload(infoPinned, tmp)
            assertTrue(
                "expected SignatureMismatch branch when actual is null",
                result is UpdateCheckResult.Failed.SignatureMismatch,
            )
            result as UpdateCheckResult.Failed.SignatureMismatch
            assertEquals("deadbeef".repeat(8), result.expected)
            assertNull("actual must be null when file is unparsable", result.actual)
        } finally {
            tmp.delete()
        }
    }
}
