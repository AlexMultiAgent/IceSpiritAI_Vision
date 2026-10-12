package com.icespiritai.offline.auth

import java.security.PublicKey
import java.security.Signature

sealed class VerifyResult {
    data class Valid(val payload: LicensePayload) : VerifyResult()
    data class UsernameMismatch(val codeUsername: String) : VerifyResult()
    data object SignatureMismatch : VerifyResult()
    data object Expired : VerifyResult()
    data object ParseFailed : VerifyResult()
}

class LicenseVerifier private constructor(private val publicKey: PublicKey) {

    fun verify(
        code: String,
        today: Long,
        claimedUsername: String? = null
    ): VerifyResult {
        val parsed = LicenseCodec.parse(code) ?: return VerifyResult.ParseFailed

        val sig = try {
            val s = Signature.getInstance("SHA256withECDSA")
            s.initVerify(publicKey)
            s.update(parsed.payloadBytes)
            s.verify(parsed.signature)
        } catch (t: Throwable) {
            return VerifyResult.SignatureMismatch
        }
        if (!sig) return VerifyResult.SignatureMismatch

        if (claimedUsername != null && parsed.payload.n != claimedUsername) {
            return VerifyResult.UsernameMismatch(parsed.payload.n)
        }

        if (parsed.payload.isExpired(today)) return VerifyResult.Expired

        return VerifyResult.Valid(parsed.payload)
    }

    companion object {
        fun fromPublicKey(key: PublicKey): LicenseVerifier = LicenseVerifier(key)
    }
}
