package com.icespiritai.offline.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OTA wire format (`0x43`). Layout taken from the official app's
 * `BluetoothController.startOtaUpdate` (payload = `{"u":"<downloadUrl>"}`)
 * and `BlePacketBuilder.buildOtaFragmentPacket` (frame + `u16` = total
 * payload length), cross-checked with the APK's `assets/app_config.json`
 * (`bleProtocol.cmdOta = 67`).
 */
class GlassesOtaProtocolTest {

    @Test
    fun upgradePayloadIsTheOfficialUrlJson() {
        val payload = GlassesFirmwareProtocol.buildUpgradePayload(
            "https://glass-dps.oss-cn-shenzhen.aliyuncs.com/ota/1789370230610_G20_V1_258.rbl",
        )
        assertEquals(
            """{"u":"https://glass-dps.oss-cn-shenzhen.aliyuncs.com/ota/1789370230610_G20_V1_258.rbl"}""",
            payload.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun chunkSizeFollowsTheOemFormula() {
        assertEquals(64, GlassesFirmwareProtocol.chunkSizeForMtu(517))
        assertEquals(64, GlassesFirmwareProtocol.chunkSizeForMtu(67))
        assertEquals(37, GlassesFirmwareProtocol.chunkSizeForMtu(40))
        // Never below the OEM's floor of 20, even on a degenerate MTU.
        assertEquals(20, GlassesFirmwareProtocol.chunkSizeForMtu(10))
    }

    @Test
    fun framesCarryTheTotalPayloadLengthAndTheChunkBytes() {
        // The real payload for the user's glasses is 87 B → two frames
        // (64 + 23), which is exactly where the OEM's "u16 = total" choice
        // matters: frame 1 declares 87 while carrying 64.
        val payload = GlassesFirmwareProtocol.buildUpgradePayload(
            "https://glass-dps.oss-cn-shenzhen.aliyuncs.com/ota/1789370230610_G20_V1_258.rbl",
        )
        val frames = GlassesFirmwareProtocol.buildUpgradeFrames(seq = 0x21, payload = payload)

        assertEquals(2, frames.size)
        assertTrue("payload should straddle two frames", payload.size > 64)
        assertEquals(87, payload.size)

        val first = frames[0]
        assertEquals(7 + 64, first.size)
        assertEquals(0x55, first[0].toInt() and 0xFF)
        assertEquals(0xAA, first[1].toInt() and 0xFF)
        assertEquals(0x21, first[2].toInt() and 0xFF)
        assertEquals(0x43, first[3].toInt() and 0xFF)
        assertEquals(0x01, first[4].toInt() and 0xFF)
        // u16 = the WHOLE payload's length, not this chunk's (OEM behaviour).
        assertEquals(payload.size and 0xFF, first[5].toInt() and 0xFF)
        assertEquals((payload.size shr 8) and 0xFF, first[6].toInt() and 0xFF)
        assertArrayEquals(payload.copyOfRange(0, 64), first.copyOfRange(7, first.size))

        val second = frames[1]
        assertEquals(7 + (payload.size - 64), second.size)
        assertEquals(payload.size and 0xFF, second[5].toInt() and 0xFF)
        assertArrayEquals(payload.copyOfRange(64, payload.size), second.copyOfRange(7, second.size))
    }

    @Test
    fun shortPayloadFitsInOneFrame() {
        val payload = GlassesFirmwareProtocol.buildUpgradePayload("https://a/b.rbl")
        val frames = GlassesFirmwareProtocol.buildUpgradeFrames(seq = 0x01, payload = payload)
        assertEquals(1, frames.size)
        assertEquals(payload.size, frames[0].size - 7)
    }

    @Test
    fun framesHonourACustomChunkSize() {
        val payload = ByteArray(50) { it.toByte() }
        val frames = GlassesFirmwareProtocol.buildUpgradeFrames(seq = 0x01, payload = payload, chunkSize = 20)
        assertEquals(3, frames.size)
        assertEquals(20, frames[0].size - 7)
        assertEquals(20, frames[1].size - 7)
        assertEquals(10, frames[2].size - 7)
    }

    @Test
    fun parsesAGlassesOtaFrameAndRejectsEverythingElse() {
        // `55 AA | 01 | 43 | 02 | 02 00 | 00 01` — a 2-byte status reply.
        val frame = byteArrayOf(
            0x55, 0xAA.toByte(), 0x01, 0x43, 0x02, 0x02, 0x00, 0x00, 0x01,
        )
        val parsed = GlassesFirmwareProtocol.parseOtaFrame(frame)
        assertEquals(0, parsed?.status)
        assertArrayEquals(byteArrayOf(0x00, 0x01), parsed?.payload)

        // Wrong command byte (a 0x51 capture status, say).
        assertNull(
            GlassesFirmwareProtocol.parseOtaFrame(
                byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x51, 0x04, 0x01, 0x00, 0x02),
            ),
        )
        // Declared length not matching the buffer.
        assertNull(
            GlassesFirmwareProtocol.parseOtaFrame(
                byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x43, 0x02, 0x05, 0x00, 0x00),
            ),
        )
        // Empty payload.
        assertNull(
            GlassesFirmwareProtocol.parseOtaFrame(
                byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x43, 0x02, 0x00, 0x00),
            ),
        )
        // Bad magic.
        assertNull(
            GlassesFirmwareProtocol.parseOtaFrame(
                byteArrayOf(0x54, 0xAA.toByte(), 0x01, 0x43, 0x02, 0x01, 0x00, 0x00),
            ),
        )
    }
}
