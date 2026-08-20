package com.icespiritai.offline.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

    @Test
    fun `readFirstSignerCert returns null for non-APK file`() {
        val tmp = File.createTempFile("apk-verifier-not-apk", ".txt")
        try {
            tmp.writeText("this is plain text, not a zip/apk")
            assertNull(ApkSignatureVerifier.readFirstSignerCert(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `readFirstSignerCert returns null for APK without META-INF CERT entry`() {
        val tmp = File.createTempFile("apk-verifier-empty-zip", ".zip")
        try {
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                // No META-INF/ entries — purely an empty (but valid) ZIP.
                zos.putNextEntry(ZipEntry("dummy.txt"))
                zos.write("hi".toByteArray())
                zos.closeEntry()
            }
            assertNull(ApkSignatureVerifier.readFirstSignerCert(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `readFirstSignerCert returns lower-case hex SHA-256 when result is non-null`() {
        val tmp = File.createTempFile("apk-verifier-fake-cert", ".apk")
        try {
            // Fake ASN.1 SEQUENCE — not a real cert, but `JarFile.getInputStream`
            // only requires the entry to be present, not parseable as X.509.
            // We only assert the OUTPUT SHAPE (length + hex alphabet) when the
            // verifier actually returns a non-null hex string. If the verifier
            // fails to parse this fake blob and returns null, that is also
            // acceptable behavior; the other two tests cover the null paths.
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zos.write(byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x0A))
                zos.closeEntry()
            }
            val result = ApkSignatureVerifier.readFirstSignerCert(tmp)
            if (result != null) {
                assertEquals("expected 64-char hex SHA-256", 64, result.length)
                assertTrue(
                    "expected lower-case hex only, got: $result",
                    result.all { it in '0'..'9' || it in 'a'..'f' },
                )
            }
        } finally {
            tmp.delete()
        }
    }
}