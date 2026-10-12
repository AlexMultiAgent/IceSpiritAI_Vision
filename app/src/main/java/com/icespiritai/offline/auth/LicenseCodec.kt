package com.icespiritai.offline.auth

import kotlinx.serialization.json.Json
import java.util.Base64

object LicenseCodec {
    const val PREFIX = "ICS1"

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    private val urlDecoder = Base64.getUrlDecoder()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()

    data class Parsed(val payload: LicensePayload, val payloadBytes: ByteArray, val signature: ByteArray)

    fun encodePayload(p: LicensePayload): ByteArray =
        json.encodeToString(LicensePayload.serializer(), p).toByteArray(Charsets.UTF_8)

    fun decodePayloadBytes(payloadBytes: ByteArray): LicensePayload? = try {
        json.decodeFromString(LicensePayload.serializer(), String(payloadBytes, Charsets.UTF_8))
    } catch (t: Throwable) {
        null
    }

    fun parse(code: String): Parsed? {
        val segments = code.split(".")
        if (segments.size != 3) return null
        if (segments[0] != PREFIX) return null

        val payloadBytes = try {
            urlDecoder.decode(segments[1])
        } catch (t: Throwable) {
            return null
        }

        val signature = try {
            urlDecoder.decode(segments[2])
        } catch (t: Throwable) {
            return null
        }

        val payload = decodePayloadBytes(payloadBytes) ?: return null
        if (payload.v != 1) return null
        if (payload.u.length != 6) return null
        if (payload.n.isBlank()) return null
        if (payload.e != 0L && payload.e.toString().length != 8) return null
        if (payload.d.length > 8) return null

        return Parsed(payload, payloadBytes, signature)
    }

    fun format(payloadBytes: ByteArray, signature: ByteArray): String {
        val p = urlEncoder.encodeToString(payloadBytes)
        val s = urlEncoder.encodeToString(signature)
        return "$PREFIX.$p.$s"
    }
}
