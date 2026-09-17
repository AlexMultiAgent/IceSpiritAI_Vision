package com.icespiritai.offline.glasses

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max
import kotlin.math.min

/**
 * Wire format for the glasses' firmware OTA (FFF0 command `0x43`).
 *
 * **The App does not push firmware.** The official app
 * (`com.deepvision_tek.glass_front` 3.1.00) builds
 * `mapOf("u" to downloadUrl)` → `gson.toJson(...)` → `getBytes()` and sends
 * *that* over BLE; the glasses fetch the `.rbl` themselves and flash it.
 * That is why the official flow insists on 蓝牙共享网络 (BT PAN) being up
 * before it starts — without it the glasses have nothing to download from.
 * Verified in `BluetoothController.startOtaUpdate` (smali 197-212 in
 * `classes2.dex`) and recorded in
 * `docs/knowledge/official-glasses-ota-protocol.md`.
 *
 * Everything here is pure `ByteArray` math — no Android, no Bluetooth — so
 * the frame layout is unit-tested against hand-written expectations.
 */
object GlassesFirmwareProtocol {

    /**
     * OTA command byte. The official app's `BleCommandConfig.cmdOta = 67`
     * (0x43) — cross-checked against the same value in the APK's
     * `assets/app_config.json` (`bleProtocol.cmdOta: 67`).
     */
    const val CMD_OTA: Byte = 0x43

    /**
     * Smallest chunk the glasses' own sender uses
     * (`sendOtaInFragments`: `max(20, resolveOtaFragmentPayloadSize(mtu - 3))`).
     */
    const val OTA_MIN_CHUNK = 20

    /** Largest chunk the OEM allows (`resolveOtaFragmentPayloadSize` → `min(64, …)`). */
    const val OTA_MAX_CHUNK = 64

    /**
     * Gap between two OTA frames (`otaFragmentSendGapMs`): 80 ms nominally,
     * with 120/500 ms variants for specific phases we have not pinned down.
     * The payload here is one short JSON, so 80 ms costs ~160 ms total.
     */
    const val OTA_FRAME_GAP_MS = 80L

    private val json = Json

    /**
     * `{"u":"<downloadUrl>"}` — the exact shape the official app sends
     * (single-entry map through Gson). Built with kotlinx-serialization here
     * so the string is escaped correctly for any URL.
     */
    fun buildUpgradePayload(downloadUrl: String): ByteArray =
        json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("u" to JsonPrimitive(downloadUrl))))
            .toByteArray(Charsets.UTF_8)

    /** Chunk size for a negotiated [mtu] — the OEM's `max(20, min(64, mtu - 3))`. */
    fun chunkSizeForMtu(mtu: Int): Int = max(OTA_MIN_CHUNK, min(OTA_MAX_CHUNK, mtu - 3))

    /**
     * Split [payload] into OTA frames.
     *
     * Frame layout is the usual FFF0 envelope with one twist: the `u16` field
     * carries the **whole payload's length**, not this frame's chunk length
     * (`BlePacketBuilder.buildOtaFragmentPacket(seq, total, fragment)` is
     * called with `array-length` of the full array). The glasses know the
     * last frame arrived when the accumulated bytes reach that total.
     */
    fun buildUpgradeFrames(
        seq: Byte,
        payload: ByteArray,
        chunkSize: Int = OTA_MAX_CHUNK,
    ): List<ByteArray> {
        require(payload.isNotEmpty()) { "OTA payload must not be empty" }
        val chunk = chunkSize.coerceIn(OTA_MIN_CHUNK, OTA_MAX_CHUNK)
        val frames = ArrayList<ByteArray>((payload.size + chunk - 1) / chunk)
        var offset = 0
        while (offset < payload.size) {
            val end = min(offset + chunk, payload.size)
            frames += buildOtaFrame(seq, payload.size, payload, offset, end)
            offset = end
        }
        return frames
    }

    private fun buildOtaFrame(
        seq: Byte,
        totalPayloadBytes: Int,
        payload: ByteArray,
        from: Int,
        to: Int,
    ): ByteArray {
        val chunkBytes = to - from
        val frame = ByteArray(7 + chunkBytes)
        frame[0] = GlassesPhotoProtocol.FFF0_MAGIC_BYTE_0
        frame[1] = GlassesPhotoProtocol.FFF0_MAGIC_BYTE_1
        frame[2] = seq
        frame[3] = CMD_OTA
        frame[4] = GlassesPhotoProtocol.TYPE_REQUEST
        frame[5] = (totalPayloadBytes and 0xFF).toByte()
        frame[6] = ((totalPayloadBytes shr 8) and 0xFF).toByte()
        payload.copyInto(frame, 7, from, to)
        return frame
    }

    /**
     * A glasses `0x43` frame seen from the App side.
     *
     * The official app only reads `payload[0]` (status) and distinguishes
     * "acked" from "failed" on it (`handleOtaPacket`, `OtaPhase.ACKED` /
     * `OtaErrorCodeMapper`). We surface the byte verbatim and let the caller
     * decide — the firmware's full status table is not documented anywhere we
     * could verify, and guessing it would be worse than reporting it.
     */
    data class OtaFrame(val status: Int, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is OtaFrame && status == other.status && payload.contentEquals(other.payload)

        override fun hashCode(): Int = 31 * status + payload.contentHashCode()
    }

    /** Parse an FFF0 `0x43` frame (Response or Notify), or `null` if it isn't one. */
    fun parseOtaFrame(frame: ByteArray): OtaFrame? {
        if (frame.size < 7) return null
        if (frame[0] != GlassesPhotoProtocol.FFF0_MAGIC_BYTE_0) return null
        if (frame[1] != GlassesPhotoProtocol.FFF0_MAGIC_BYTE_1) return null
        if (frame[3] != CMD_OTA) return null
        val len = (frame[5].toInt() and 0xFF) or ((frame[6].toInt() and 0xFF) shl 8)
        if (frame.size != 7 + len) return null
        if (len == 0) return null
        return OtaFrame(status = frame[7].toInt() and 0xFF, payload = frame.copyOfRange(7, frame.size))
    }
}
