package com.icespiritai.offline.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Firmware-version read (`0x10` device-info + sub-command `0x20`).
 *
 * Byte layouts come from two independent sources that agree:
 * `com.deepvision_tek.glass_front` 3.1.00 (`BleCommandConfig$Companion.default()`
 * constructor args: `getDeviceInfoCmd = 16`, `subFirmwareInfo = 32`) and the
 * vendor's reference project, which builds and parses the very same TLV
 * (`BlePacketBuilder.buildFirmwareVersionRequestPacket`,
 * `BluetoothController.handleDeviceInfoPayload`).
 */
class GlassesFirmwareVersionTest {

    private fun fff0(seq: Int, cmd: Int, type: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(7 + payload.size)
        frame[0] = 0x55
        frame[1] = 0xAA.toByte()
        frame[2] = seq.toByte()
        frame[3] = cmd.toByte()
        frame[4] = type.toByte()
        frame[5] = (payload.size and 0xFF).toByte()
        frame[6] = ((payload.size shr 8) and 0xFF).toByte()
        payload.copyInto(frame, 7)
        return frame
    }

    private fun tlvVersion(version: String): ByteArray {
        val bytes = version.toByteArray(Charsets.UTF_8)
        return byteArrayOf(GlassesPhotoProtocol.SUB_FIRMWARE_INFO, bytes.size.toByte()) + bytes
    }

    @Test
    fun versionRequestMatchesTheVendorReferenceFrame() {
        // `55 AA | 01 | 10 | 01 | 02 00 | 20 00`
        val frame = GlassesPhotoProtocol.buildFirmwareVersionRequestFrame(seq = 0x01)
        val expected = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x01,
            0x10,               // cmd = getDeviceInfoCmd
            0x01,               // type = Request
            0x02, 0x00,         // payload length u16 LE = 2
            0x20,               // sub-command = firmware info
            0x00,               // TLV length 0 → "report it"
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun parsesVersionFromTheDeviceInfoResponse() {
        val frame = fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = tlvVersion("V2.4.5"))

        assertEquals("V2.4.5", GlassesPhotoProtocol.parseFirmwareVersion(frame))
        assertTrue(GlassesPhotoProtocol.isFirmwareVersionResponse(frame))
    }

    @Test
    fun parsesVersionFromADeviceStatusNotifyTlvList() {
        // 0x11 status notifies carry a list of TLVs; the firmware one is not
        // necessarily first (vendor reference handleDeviceStatusNotify walks
        // the list). Battery = type 0x01, version = 0x20.
        val payload = byteArrayOf(0x01, 0x01, 0x64) + tlvVersion("V2.4.5") + byteArrayOf(0x17, 0x02, 0x05, 0x00)
        val frame = fff0(seq = 0x02, cmd = 0x11, type = 0x04, payload = payload)

        assertEquals("V2.4.5", GlassesPhotoProtocol.parseFirmwareVersion(frame))
        // It is not the *response* to our read, so the UI keeps waiting for a
        // 0x10 Response while still being able to use this.
        assertFalse(GlassesPhotoProtocol.isFirmwareVersionResponse(frame))
    }

    @Test
    fun trimsNulPaddingAndWhitespace() {
        val frame = fff0(
            seq = 0x01,
            cmd = 0x10,
            type = 0x02,
            payload = byteArrayOf(0x20, 0x08) + "V2.4.5\u0000\u0000".toByteArray(Charsets.UTF_8),
        )

        assertEquals("V2.4.5", GlassesPhotoProtocol.parseFirmwareVersion(frame))
    }

    @Test
    fun ignoresFramesThatAreNotAFirmwareAnswer() {
        // A 0x51 capture status: same envelope, no firmware TLV.
        assertNull(
            GlassesPhotoProtocol.parseFirmwareVersion(
                fff0(seq = 0x03, cmd = 0x51, type = 0x04, payload = byteArrayOf(0x02)),
            ),
        )
        // 0x10 response with a different sub-command (e.g. memory info).
        assertNull(
            GlassesPhotoProtocol.parseFirmwareVersion(
                fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = byteArrayOf(0x17, 0x02, 0x01, 0x00)),
            ),
        )
        // Zero-length version → not an answer.
        assertNull(
            GlassesPhotoProtocol.parseFirmwareVersion(
                fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = byteArrayOf(0x20, 0x00)),
            ),
        )
        // Truncated TLV (declares 9 bytes, carries 3).
        assertNull(
            GlassesPhotoProtocol.parseFirmwareVersion(
                fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = byteArrayOf(0x20, 0x09, 0x56, 0x32, 0x2E)),
            ),
        )
        // Bad magic.
        assertNull(
            GlassesPhotoProtocol.parseFirmwareVersion(
                byteArrayOf(0x00, 0x00, 0x01, 0x10, 0x02, 0x03, 0x00, 0x20, 0x01, 0x41),
            ),
        )
    }

    @Test
    fun truncatedStatusListDoesNotStealAVersionFromLaterBytes() {
        // First TLV claims 5 bytes but only 1 is present: the vendor
        // reference stops walking there, and so do we — parsing past a
        // broken boundary would read payload bytes as if they were a header.
        val payload = byteArrayOf(0x01, 0x05, 0x64, 0x20, 0x01, 0x41)
        assertNull(
            GlassesPhotoProtocol.parseFirmwareVersion(
                fff0(seq = 0x02, cmd = 0x11, type = 0x04, payload = payload),
            ),
        )
    }
}
