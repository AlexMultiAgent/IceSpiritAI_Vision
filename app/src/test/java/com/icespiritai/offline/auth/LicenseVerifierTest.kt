package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseVerifierTest {

    private fun generateKeyPair(): java.security.KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    private fun sign(pk: java.security.PrivateKey, data: ByteArray): ByteArray {
        val s = java.security.Signature.getInstance("SHA256withECDSA")
        s.initSign(pk)
        s.update(data)
        return s.sign()
    }

    private fun makeCode(kp: java.security.KeyPair, e: Long = 20270630, u: String = "a1b2c3", n: String = "acme"): String {
        val p = LicensePayload(1, u, n, e)
        val bytes = LicenseCodec.encodePayload(p)
        val sig = sign(kp.private, bytes)
        return LicenseCodec.format(bytes, sig)
    }

    @Test
    fun accepts_well_formed_signed_code() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp)
        val r = verifier.verify(code, today = 20260101)
        assertTrue(r is VerifyResult.Valid)
        val valid = r as VerifyResult.Valid
        assertEquals("a1b2c3", valid.payload.u)
        assertEquals("acme", valid.payload.n)
    }

    @Test
    fun rejects_tampered_payload() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp)
        val parts = code.split(".")
        val tamperedPayloadBytes = LicenseCodec.encodePayload(
            LicensePayload(1, "a1b2c3", "evil", 20270630)
        )
        val tamperedCode = "ICS1.${Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadBytes)}.${parts[2]}"
        val r = verifier.verify(tamperedCode, today = 20260101)
        assertEquals(VerifyResult.SignatureMismatch, r)
    }

    @Test
    fun rejects_wrong_public_key() {
        val kp1 = generateKeyPair()
        val kp2 = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp2.public)
        val code = makeCode(kp1)
        val r = verifier.verify(code, today = 20260101)
        assertEquals(VerifyResult.SignatureMismatch, r)
    }

    @Test
    fun rejects_expired() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, e = 20260101)
        val r = verifier.verify(code, today = 20260102)
        assertEquals(VerifyResult.Expired, r)
    }

    @Test
    fun accepts_permanent_zero_even_far_future() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, e = 0)
        val r = verifier.verify(code, today = 20990101)
        assertTrue(r is VerifyResult.Valid)
    }

    @Test
    fun username_mismatch_returns_specific_error() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, n = "acme")
        val r = verifier.verify(code, today = 20260101, claimedUsername = "wrong")
        assertEquals(VerifyResult.UsernameMismatch("acme"), r)
    }

    @Test
    fun username_match_returns_valid() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, n = "acme")
        val r = verifier.verify(code, today = 20260101, claimedUsername = "acme")
        assertTrue(r is VerifyResult.Valid)
    }
}
