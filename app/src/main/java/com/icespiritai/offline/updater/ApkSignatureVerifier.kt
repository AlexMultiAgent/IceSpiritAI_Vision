package com.icespiritai.offline.updater

import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.jar.JarFile

/**
 * Outcome of comparing an APK's v1 signer-cert fingerprint against the
 * pinned `AppVersionInfo.signerCertSha256`.
 *
 * - [Match]  — cert present and equals the expected SHA-256 (case-insensitive).
 *   Empty `actualCertSha256` represents the v1-spec "skip gate" path (empty
 *   expected), which the in-app update flow treats as "no pinning enforced,
 *   accept".
 * - [Mismatch] — expected was non-empty AND (no cert could be parsed from
 *   the APK, or the parsed cert's SHA-256 did not match). `actual = null`
 *   means the verifier could not even produce a fingerprint (no v1
 *   signature block, parse failure, etc.) — caller MUST treat this as
 *   "unverifiable, block the update", same as a non-null mismatch.
 */
sealed class VerifierResult {
    data class Match(val actualCertSha256: String) : VerifierResult()
    data class Mismatch(val expected: String, val actual: String?) : VerifierResult()
}

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
 * double-gate compares this fingerprint against
 * `AppVersionInfo.signerCertSha256` to defeat tampering between upload
 * and download. If the
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
     *
     * Kept public + `@VisibleForTesting` so unit tests can drive the raw
     * fingerprint path. Production callers should use [verify], which wraps
     * this into a [VerifierResult] suitable for pattern-matching.
     */
    @VisibleForTesting
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
     * Compare the APK's v1 signer-cert fingerprint against [expectedCertSha256].
     *
     * Returns:
     *  - [VerifierResult.Match] when [expectedCertSha256] is empty (v1-spec
     *    "skip gate" path — no pinning enforced, accept). The carried
     *    `actualCertSha256` is the empty string in that case, NOT the
     *    actual fingerprint, so callers don't accidentally log it as
     *    "verified against".
     *  - [VerifierResult.Match] when [expectedCertSha256] is non-empty and
     *    case-insensitively matches the parsed cert's SHA-256 hex.
     *  - [VerifierResult.Mismatch] when [expectedCertSha256] is non-empty
     *    AND the cert could not be parsed (`actual = null`) or its SHA-256
     *    did not match (`actual = "<parsed hex>"`). Caller MUST treat both
     *    shapes as "block the update".
     *
     * The case-insensitive comparison matters because [readFirstSignerCert]
     * emits lower-case hex but the pinned value in `vision-latest.json` may
     * carry any casing depending on which tool computed it; build-time
     * `extractApkCertificateSha256` happens to also emit lower-case, but the
     * runtime must not assume that.
     */
    fun verify(apk: File, expectedCertSha256: String): VerifierResult {
        if (expectedCertSha256.isEmpty()) return VerifierResult.Match("")
        val actual = readFirstSignerCert(apk)
        return if (actual != null && actual.equals(expectedCertSha256, ignoreCase = true)) {
            VerifierResult.Match(actual)
        } else {
            VerifierResult.Mismatch(expected = expectedCertSha256, actual = actual)
        }
    }

    /**
     * Parse the bytes of a `META-INF/CERT.*` entry as a PKCS#7 SignedData
     * blob and return the first inner X.509 certificate (DER-encoded).
     *
     * The CERT.* entry on disk is a PKCS#7 SignedData wrapper, but
     * `CertificateFactory.generateCertificates(InputStream)` transparently
     * walks past the wrapper and yields the inner X.509 certificates. We
     * feed the **entire** stream (no ASN.1 envelope stripping) so that this
     * runtime path produces byte-for-byte the same `Certificate.encoded`
     * DER as the build-time `extractApkCertificateSha256` helper, which
     * also calls `generateCertificates(bytes.inputStream())`. Both ends
     * MUST apply the SAME parsing — `generateCertificate` (singular) and
     * `generateCertificates` (plural) produce DIFFERENT DER on some JDKs
     * for the same PKCS#7 stream, so a mismatch silently rejects every
     * legitimate in-app update.
     */
    private fun parseFirstCertificate(bytes: ByteArray): java.security.cert.Certificate? = try {
        CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(bytes))
            ?.firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}