package com.icespiritai.offline.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `0x11` device-status TLVs — the channel the glasses use to report things
 * *they* did, including the hardware shutter button.
 *
 * The reference frames are real captures from the device on 2026-09-17
 * (Glasses-A88 V2.5.8) and the TLV table matches the OEM app's
 * `parseDeviceStatusNotifyPayload` / `BleCommandConfig` constants.
 */
class GlassesDeviceStatusTlvTest {

    /** `55 AA | seq | 11 | 03 | len u16 LE | payload` — the notify envelope. */
    private fun statusFrame(seq: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(7 + payload.size)
        frame[0] = 0x55
        frame[1] = 0xAA.toByte()
        frame[2] = seq.toByte()
        frame[3] = 0x11
        frame[4] = 0x03
        frame[5] = (payload.size and 0xFF).toByte()
        frame[6] = ((payload.size shr 8) and 0xFF).toByte()
        payload.copyInto(frame, 7)
        return frame
    }

    private fun tlv(type: Int, value: ByteArray): ByteArray =
        byteArrayOf(type.toByte(), value.size.toByte()) + value

    @Test
    fun shutterButtonFrameIsRecognised() {
        // Literal capture from the phone: `55aa1511030300170100`
        // (TLV type 0x17 = mediaPhotoResult, len 1, value 0 = success).
        val frame = byteArrayOf(
            0x55, 0xAA.toByte(), 0x15, 0x11, 0x03, 0x03, 0x00, 0x17, 0x01, 0x00,
        )
        assertTrue(GlassesPhotoProtocol.reportsShutterPhoto(frame))
    }

    @Test
    fun shutterPhotoResultIsInvertedLikeTheFirmware() {
        // The firmware's 1-byte booleans use 0 = true (OEM inverts them too).
        assertTrue(GlassesPhotoProtocol.reportsShutterPhoto(statusFrame(1, tlv(0x17, byteArrayOf(0x00)))))
        assertFalse(GlassesPhotoProtocol.reportsShutterPhoto(statusFrame(1, tlv(0x17, byteArrayOf(0x01)))))
    }

    @Test
    fun batteryAndFirmwareFramesAreNotShutterEvents() {
        // Real battery frame: `55aa1611030300010151` (type 0x01).
        assertFalse(
            GlassesPhotoProtocol.reportsShutterPhoto(
                byteArrayOf(0x55, 0xAA.toByte(), 0x16, 0x11, 0x03, 0x03, 0x00, 0x01, 0x01, 0x51),
            ),
        )
        // A 0x10 firmware-version response is a different command entirely.
        assertFalse(
            GlassesPhotoProtocol.reportsShutterPhoto(
                byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x10, 0x02, 0x08, 0x00, 0x20, 0x06, 0x56, 0x32, 0x2E, 0x34, 0x2E, 0x36),
            ),
        )
    }

    @Test
    fun parsesEveryTlvInOneFrame() {
        val payload = tlv(0x01, byteArrayOf(0x51)) +
            tlv(0x17, byteArrayOf(0x00)) +
            tlv(0x15, byteArrayOf(0x00)) +
            tlv(0x20, "V2.5.8".toByteArray())
        val tlvs = GlassesPhotoProtocol.parseDeviceStatusTlvs(statusFrame(2, payload))

        assertEquals(4, tlvs.size)
        assertEquals(listOf(0x01, 0x17, 0x15, 0x20), tlvs.map { it.type })
        assertEquals("V2.5.8", tlvs[3].value.toString(Charsets.UTF_8))
        // PAN state and photo result both ride in the same frame.
        assertEquals(true, GlassesPhotoProtocol.reportsNetworkSharingOn(statusFrame(2, payload)))
        assertTrue(GlassesPhotoProtocol.reportsShutterPhoto(statusFrame(2, payload)))
    }

    @Test
    fun networkSharingFlagIsNullWhenAbsent() {
        assertNull(GlassesPhotoProtocol.reportsNetworkSharingOn(statusFrame(1, tlv(0x01, byteArrayOf(0x51)))))
        // Present but off (value 1 = false in the firmware's convention).
        assertEquals(false, GlassesPhotoProtocol.reportsNetworkSharingOn(statusFrame(1, tlv(0x15, byteArrayOf(0x01)))))
    }

    @Test
    fun malformedFrameOrTlvStopsTheWalk() {
        // Truncated TLV: declares 5 bytes, carries 2. The first (valid) TLV is
        // still reported, and the truncated one must not be invented.
        val payload = tlv(0x01, byteArrayOf(0x51)) + byteArrayOf(0x17, 0x05, 0x00)
        val tlvs = GlassesPhotoProtocol.parseDeviceStatusTlvs(statusFrame(3, payload))
        assertEquals(listOf(0x01), tlvs.map { it.type })
        assertFalse(GlassesPhotoProtocol.reportsShutterPhoto(statusFrame(3, payload)))

        // Not a 0x11 frame at all.
        assertTrue(
            GlassesPhotoProtocol.parseDeviceStatusTlvs(
                byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x51, 0x03, 0x01, 0x00, 0x02),
            ).isEmpty(),
        )
    }
}
