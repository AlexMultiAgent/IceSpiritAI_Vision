package com.icespiritai.offline.updater

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.jar.JarFile

/**
 * Reads the v1 signer certificate SHA-256 fingerprint from an APK file.
 *
 * This is the **runtime mirror** of the build-time helper
 * `extractApkCertificateSha256` defined at `app/build.gradle.kts:418-439`,
 * and the source-of-truth field `AppVersionInfo.signerCertSha256`
 * (pinned in `vision-latest.json`).
 *
 * All three paths — build-time helper, this runtime verifier, and the
 * JSON field — MUST stay byte-for-byte aligned: the in-app update
 * double-gate compares this fingerprint against `AppVersionInfo.apkSha256`
 * of the download to defeat tampering between upload and download. If the
 * build helper computes one hash and this function computes a different
 * one for the same APK, every legitimate update is rejected.
 *
 * The v1 path is required because `enableV1Signing = true` is the only
 * way to emit `META-INF/CERT.RSA`; AGP defaults to v2-only when
 * `enableV1Signing` is unset, and without `CERT.RSA` this function returns
 * `null` and every legitimate update is blocked.
 */
object ApkSignatureVerifier {

    private const val TAG = "ApkSignatureVerifier"

    /** v1 signer cert entry names, in canonical preference order. */
    private val CERT_ENTRIES = listOf(
        "META-INF/CERT.RSA",
        "META-INF/CERT.DSA",
        "META-INF/CERT.EC",
    )

    /**
     * Returns the lower-case 64-char hex SHA-256 fingerprint of the first
     * v1 signer certificate found in [apk], or `null` if:
     *  - the file does not exist / is not a valid JAR/ZIP,
     *  - no `META-INF/CERT.{RSA,DSA,EC}` entry is present (APK is v2/v3-only),
     *  - the entry cannot be parsed as X.509,
     *  - the underlying JAR / certificate machinery throws any exception.
     *
     * Any failure is logged at warn level (class name only, no PII / cert
     * bytes) and returns `null` — callers treat `null` as "unverifiable,
     * block the update".
     */
    fun readFirstSignerCert(apk: File): String? = try {
        if (!apk.exists()) return null
        JarFile(apk).use { jar ->
            for (entryName in CERT_ENTRIES) {
                val entry = jar.getJarEntry(entryName) ?: continue
                val cert = parseFirstCertificate(jar.getInputStream(entry).use { it.readBytes() })
                    ?: continue
                return sha256Hex(cert.encoded)
            }
        }
        null
    } catch (t: Throwable) {
        Log.w(TAG, "readFirstSignerCert failed: ${t.javaClass.simpleName}")
        null
    }

    /**
     * Parse the bytes of a `META-INF/CERT.*` entry as a PKCS#7 SignedData
     * blob and return the first inner X.509 certificate (DER-encoded).
     *
     * The CERT.* entry on disk is a PKCS#7 SignedData wrapper, but
     * `CertificateFactory.generateCertificate(InputStream)` transparently
     * walks past the wrapper and yields the inner X.509 certificate. We
     * feed the **entire** stream (no ASN.1 envelope stripping) so that this
     * runtime path produces byte-for-byte the same `Certificate.encoded`
     * DER as the build-time `extractApkCertificateSha256` helper, which
     * also calls `generateCertificates(bytes.inputStream())`. Both ends
     * must apply the SAME parsing for SHA-256 to match across the
     * build/runtime boundary.
     */
    private fun parseFirstCertificate(bytes: ByteArray): java.security.cert.Certificate? = try {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes))
    } catch (_: Exception) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}