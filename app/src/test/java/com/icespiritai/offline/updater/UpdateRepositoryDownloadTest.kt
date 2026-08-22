package com.icespiritai.offline.updater

import com.icespiritai.offline.BuildConfig
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
    fun signatureGate_skipsWhenExpectedCertIsEmpty() {
        // A build that compiles no pin (expectedCertSha256 == "") must not block
        // updates — applies to the legacy "no pinning enforced" path.
        val tmp = Files.createTempFile("icespirit-notapk", ".bin").toFile()
        tmp.writeText("not a real apk, but the verifier must not even be invoked")
        val infoNoCert = info.copy(signerCertSha256 = "")
        try {
            val result = UpdateRepository.verifySignatureForDownload(
                infoNoCert, tmp, expectedCertSha256 = "",
            )
            assertNull(
                "empty expected cert must skip the cert-pin gate (download → ReadyToInstall)",
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
            val result = UpdateRepository.verifySignatureForDownload(
                infoPinned, tmp, expectedCertSha256 = expectedHex,
            )
            assertTrue(
                "expected SignatureMismatch branch when actual is null",
                result is UpdateCheckResult.Failed.SignatureMismatch,
            )
            result as UpdateCheckResult.Failed.SignatureMismatch
            assertEquals(expectedHex, result.expected)
            assertNull("actual must be null when file is unparsable", result.actual)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun signatureGate_enforcesClientPinWhenJsonOmitsCert() {
        // The client pin (BuildConfig.UPDATE_EXPECTED_CERT_SHA256) must be the
        // trust anchor: even when vision-latest.json omits signerCertSha256,
        // an unparsable/bad APK is rejected against the pinned value instead of
        // being accepted for "backward compat".
        val tmp = Files.createTempFile("icespirit-notapk3", ".bin").toFile()
        tmp.writeText("plain text — not a real APK")
        try {
            val result = UpdateRepository.verifySignatureForDownload(
                info, tmp, // no override → resolves to BuildConfig pin
            )
            assertTrue(result is UpdateCheckResult.Failed.SignatureMismatch)
            result as UpdateCheckResult.Failed.SignatureMismatch
            assertEquals(BuildConfig.UPDATE_EXPECTED_CERT_SHA256, result.expected)
            assertNull(result.actual)
        } finally {
            tmp.delete()
        }
    }
}
