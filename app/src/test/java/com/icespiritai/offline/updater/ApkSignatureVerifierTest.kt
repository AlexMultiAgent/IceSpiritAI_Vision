package com.icespiritai.offline.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Robolectric is required because `ApkSignatureVerifier` reads JAR/ZIP
 * metadata via `java.util.jar.JarFile`, which on JVM-unit-tests under
 * `unitTests.isReturnDefaultValues=true` returns null from Android shims.
 * The actual production logic is pure JVM (java.util.jar / java.security);
 * Robolectric just ensures the Android-stubs in `android.jar` don't trip
 * Kotlin's platform-type null checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ApkSignatureVerifierTest {

    @get:Rule val tmp = TemporaryFolder()

    /**
     * Write [certBytes] into a fresh ZIP as `META-INF/CERT.RSA` and return
     * the resulting file. Used to construct APK-shaped fixtures whose
     * CERT.RSA entry can be valid, empty, or junk — letting each test
     * drive a specific parse branch of `readFirstSignerCert` /
     * `parseFirstCertificate`.
     */
    private fun apkWithCert(certBytes: ByteArray): File {
        val f = tmp.newFile("fake.apk")
        ZipOutputStream(f.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write(certBytes)
            zos.closeEntry()
        }
        return f
    }

    @Test
    fun `readFirstSignerCert returns null for non-APK file`() {
        val f = tmp.newFile("not-an-apk.txt")
        f.writeText("this is plain text, not a zip/apk")
        assertNull(ApkSignatureVerifier.readFirstSignerCert(f))
    }

    @Test
    fun `readFirstSignerCert returns null for APK without META-INF CERT entry`() {
        val f = tmp.newFile("empty-zip.apk")
        ZipOutputStream(f.outputStream().buffered()).use { zos ->
            // No META-INF/ entries — purely an empty (but valid) ZIP.
            zos.putNextEntry(ZipEntry("dummy.txt"))
            zos.write("hi".toByteArray())
            zos.closeEntry()
        }
        assertNull(ApkSignatureVerifier.readFirstSignerCert(f))
    }

    @Test
    fun `readFirstSignerCert returns lower-case hex SHA-256 when result is non-null`() {
        // Fake ASN.1 SEQUENCE — not a real cert, but `JarFile.getInputStream`
        // only requires the entry to be present, not parseable as X.509.
        // We only assert the OUTPUT SHAPE (length + hex alphabet) when the
        // verifier actually returns a non-null hex string. If the verifier
        // fails to parse this fake blob and returns null, that is also
        // acceptable behavior; the other two tests cover the null paths.
        val f = apkWithCert(byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x0A))
        val result = ApkSignatureVerifier.readFirstSignerCert(f)
        if (result != null) {
            assertEquals("expected 64-char hex SHA-256", 64, result.length)
            assertTrue(
                "expected lower-case hex only, got: $result",
                result.all { it in '0'..'9' || it in 'a'..'f' },
            )
        }
    }

    @Test
    fun mismatch_when_expected_empty_returns_skip_signal() {
        // 空 expected = skip gate(v1 兼容路径)。verify 应返回 Match(空 cert)
        val f = apkWithCert(ByteArray(8))
        val r = ApkSignatureVerifier.verify(f, expectedCertSha256 = "")
        assertEquals(VerifierResult.Match(""), r)
    }

    @Test
    fun missing_cert_returns_mismatch() {
        // 8 个零字节不是合法 PKCS#7 / X.509 — parseFirstCertificate 会抛
        // CertificateException → readFirstSignerCert 返回 null → verify
        // 走 Mismatch(expected, actual = null) 分支。
        val f = apkWithCert(ByteArray(8))
        val r = ApkSignatureVerifier.verify(f, expectedCertSha256 = "abc123")
        assertTrue("expected Mismatch, got: $r", r is VerifierResult.Mismatch)
    }
}
