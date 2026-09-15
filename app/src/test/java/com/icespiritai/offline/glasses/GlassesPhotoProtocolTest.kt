package com.icespiritai.offline.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GlassesPhotoProtocol]. Pure JVM, no Android or Bluetooth
 * mocks needed — every constant and parser/builder is exercised against
 * hand-crafted byte arrays.
 *
 * Byte layouts verified against `docs/glasses/AI识图传图提速_App连接参数配合.md`
 * §6 BLE / FA10 协议细节.
 */
class GlassesPhotoProtocolTest {

    // ────────────────────────────────────────────────────────────────────
    // FFF0 / 0x33 capture request frame builder (Glasses-A88 V2.4.5 verified)
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun buildCaptureRequestFrame_defaultQuality_matchesSpec() {
        // 55 AA | seq | 33 | 01 | 0001 | quality(80)
        // (OEM 0x50 was rejected by V2.4.5 firmware with err=0x01)
        val frame = GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x07)
        val expected = byteArrayOf(
            0x55, 0xAA.toByte(), // magic
            0x07,               // seq
            0x33,               // cmd = aiPhotoBleCmd (V2.4.5 verified)
            0x01,               // type=Request
            0x01, 0x00,         // payload length u16 LE = 1
            0x50,               // quality = 80
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun buildCaptureRequestFrame_customQuality_overridesDefault() {
        val frame = GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x00, quality = 0x64)
        assertEquals(0x64.toByte(), frame[7])
    }

    @Test
    fun buildCaptureRequestFrame_incrementsWithSeq() {
        val f1 = GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x10)
        val f2 = GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x11)
        assertEquals(0x10.toByte(), f1[2])
        assertEquals(0x11.toByte(), f2[2])
    }

    @Test
    fun buildCaptureRequestFrame_isAlways8Bytes() {
        // 2 magic + 1 seq + 1 cmd + 1 type + 2 len + 1 quality = 8
        val frame = GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x00)
        assertEquals(8, frame.size)
    }

    // ────────────────────────────────────────────────────────────────────
    // 0x51 status notify parser
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun parseStatusNotify_startWithFileSize() {
        // 55 AA | seq | 51 | 04 (notify type) | 05 00 | 01 | fileSize u32 LE
        val fileSize = 18_432
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x00,                    // seq
            0x51,                    // cmd
            GlassesPhotoProtocol.TYPE_NOTIFY, // type
            0x05, 0x00,              // payload length = 5
            GlassesPhotoProtocol.STATUS_START,
            (fileSize and 0xFF).toByte(),
            ((fileSize ushr 8) and 0xFF).toByte(),
            ((fileSize ushr 16) and 0xFF).toByte(),
            ((fileSize ushr 24) and 0xFF).toByte(),
        )
        val parsed = GlassesPhotoProtocol.parseStatusNotify(notify)
        assertNotNull(parsed)
        assertTrue(parsed is GlassesPhotoProtocol.StatusNotify.Start)
        assertEquals(fileSize, (parsed as GlassesPhotoProtocol.StatusNotify.Start).fileSize)
    }

    @Test
    fun parseStatusNotify_startWithoutFileSize() {
        // 55 AA | seq | 51 | 04 | 01 00 | 01
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x01,
            0x51,
            GlassesPhotoProtocol.TYPE_NOTIFY,
            0x01, 0x00,
            GlassesPhotoProtocol.STATUS_START,
        )
        val parsed = GlassesPhotoProtocol.parseStatusNotify(notify)
        assertTrue(parsed is GlassesPhotoProtocol.StatusNotify.Start)
        assertNull((parsed as GlassesPhotoProtocol.StatusNotify.Start).fileSize)
    }

    @Test
    fun parseStatusNotify_startWithFileSizeAtMaxUnsigned() {
        val fileSize = 0xFFFFFFFFL.toInt()  // 2^32 - 1, the largest representable u32
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x00, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x05, 0x00,
            GlassesPhotoProtocol.STATUS_START,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        )
        val parsed = GlassesPhotoProtocol.parseStatusNotify(notify)
        assertTrue(parsed is GlassesPhotoProtocol.StatusNotify.Start)
        assertEquals(fileSize, (parsed as GlassesPhotoProtocol.StatusNotify.Start).fileSize)
    }

    @Test
    fun parseStatusNotify_success() {
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x02, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x01, 0x00,
            GlassesPhotoProtocol.STATUS_SUCCESS,
        )
        val parsed = GlassesPhotoProtocol.parseStatusNotify(notify)
        assertTrue(parsed is GlassesPhotoProtocol.StatusNotify.Success)
    }

    @Test
    fun parseStatusNotify_failed() {
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x03, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x01, 0x00,
            GlassesPhotoProtocol.STATUS_FAILED,
        )
        val parsed = GlassesPhotoProtocol.parseStatusNotify(notify)
        assertTrue(parsed is GlassesPhotoProtocol.StatusNotify.Failed)
    }

    @Test
    fun parseStatusNotify_legacyFtpReady() {
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x04, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x01, 0x00,
            GlassesPhotoProtocol.STATUS_LEGACY_FTP_READY,
        )
        val parsed = GlassesPhotoProtocol.parseStatusNotify(notify)
        assertTrue(parsed is GlassesPhotoProtocol.StatusNotify.LegacyFtpReady)
    }

    @Test
    fun parseStatusNotify_badMagic_returnsNull() {
        // 66 BB instead of 55 AA
        val notify = byteArrayOf(0x66, 0xBB.toByte(), 0x00, 0x51, 0x04, 0x01, 0x00, 0x01)
        assertNull(GlassesPhotoProtocol.parseStatusNotify(notify))
    }

    @Test
    fun parseStatusNotify_truncated_returnsNull() {
        // Length claims 5 bytes of payload but only 1 byte present
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x00, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x05, 0x00,
            GlassesPhotoProtocol.STATUS_START,
            // missing the 4-byte file size
        )
        assertNull(GlassesPhotoProtocol.parseStatusNotify(notify))
    }

    @Test
    fun parseStatusNotify_tooShort_returnsNull() {
        // Less than the 7-byte frame header
        val notify = byteArrayOf(0x55, 0xAA.toByte(), 0x00)
        assertNull(GlassesPhotoProtocol.parseStatusNotify(notify))
    }

    @Test
    fun parseStatusNotify_wrongCmd_returnsNull() {
        // 0x52 instead of 0x51
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x00, 0x52, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x01, 0x00,
            GlassesPhotoProtocol.STATUS_SUCCESS,
        )
        assertNull(GlassesPhotoProtocol.parseStatusNotify(notify))
    }

    @Test
    fun parseStatusNotify_emptyPayload_returnsNull() {
        // 55 AA | seq | 51 | 04 | 00 00 — zero-length payload, can't carry a status byte
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x00, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x00, 0x00,
        )
        assertNull(GlassesPhotoProtocol.parseStatusNotify(notify))
    }

    @Test
    fun parseStatusNotify_unknownStatusByte_returnsNull() {
        // First byte is 0x7F, not in {0x01, 0x02, 0x03, 0x04}
        val notify = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x00, 0x51, GlassesPhotoProtocol.TYPE_NOTIFY,
            0x01, 0x00,
            0x7F,
        )
        assertNull(GlassesPhotoProtocol.parseStatusNotify(notify))
    }

    // ────────────────────────────────────────────────────────────────────
    // FA10 / FA12 photo chunk parser
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun parsePhotoChunk_basicCase() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val payload = byteArrayOf(0x10, 0x00, 0x00, 0x00, 1, 2, 3, 4, 5)
        val chunk = GlassesPhotoProtocol.parsePhotoChunk(payload)
        assertNotNull(chunk)
        assertEquals(0x10, chunk!!.offset)
        assertArrayEquals(data, chunk.data)
    }

    @Test
    fun parsePhotoChunk_zeroOffset() {
        val payload = byteArrayOf(0, 0, 0, 0, 0xAA.toByte(), 0xBB.toByte())
        val chunk = GlassesPhotoProtocol.parsePhotoChunk(payload)
        assertNotNull(chunk)
        assertEquals(0, chunk!!.offset)
        assertEquals(2, chunk.data.size)
    }

    @Test
    fun parsePhotoChunk_returnsCopy_notAliasedToInput() {
        // If the parser returned the input's tail slice directly, mutating
        // the input later would silently corrupt the chunk — common bug
        // when chaining Bluetooth stack's reusable buffers. Verify copy.
        val payload = byteArrayOf(0, 0, 0, 0, 0x42)
        val chunk = GlassesPhotoProtocol.parsePhotoChunk(payload)!!
        payload[4] = 0x00
        assertEquals(0x42.toByte(), chunk.data[0])
    }

    @Test
    fun parsePhotoChunk_truncated_returnsNull() {
        // Less than 4 bytes (offset prefix) + 1 byte (min data) = 5
        val payload = byteArrayOf(0, 0, 0, 0)
        assertNull(GlassesPhotoProtocol.parsePhotoChunk(payload))
    }

    @Test
    fun parsePhotoChunk_emptyAfterOffset_returnsNull() {
        // 4-byte offset prefix and zero bytes of data — degenerate, reject.
        val payload = byteArrayOf(0, 0, 0, 0)
        assertNull(GlassesPhotoProtocol.parsePhotoChunk(payload))
    }

    // ────────────────────────────────────────────────────────────────────
    // FA11 control opcodes
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun buildFa11Resend_layout() {
        val out = GlassesPhotoProtocol.buildFa11Resend(offset = 0x1234)
        assertEquals(5, out.size)
        assertEquals(GlassesPhotoProtocol.FA11_OP_RESEND, out[0])
        assertEquals(0x34.toByte(), out[1])
        assertEquals(0x12.toByte(), out[2])
        assertEquals(0x00.toByte(), out[3])
        assertEquals(0x00.toByte(), out[4])
    }

    @Test
    fun buildFa11Resend_zeroOffset() {
        val out = GlassesPhotoProtocol.buildFa11Resend(offset = 0)
        assertEquals(GlassesPhotoProtocol.FA11_OP_RESEND, out[0])
        assertEquals(0, out[1].toInt())
        assertEquals(0, out[2].toInt())
        assertEquals(0, out[3].toInt())
        assertEquals(0, out[4].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildFa11Resend_negativeOffset_throws() {
        GlassesPhotoProtocol.buildFa11Resend(offset = -1)
    }

    @Test
    fun buildFa11Crc_layout() {
        val out = GlassesPhotoProtocol.buildFa11Crc(crc32 = 0xDEADBEEFL)
        assertEquals(5, out.size)
        assertEquals(GlassesPhotoProtocol.FA11_OP_CRC, out[0])
        // 0xDEADBEEF LE: EF BE AD DE
        assertEquals(0xEF.toByte(), out[1])
        assertEquals(0xBE.toByte(), out[2])
        assertEquals(0xAD.toByte(), out[3])
        assertEquals(0xDE.toByte(), out[4])
    }

    @Test
    fun buildFa11Cancel_layout() {
        val out = GlassesPhotoProtocol.buildFa11Cancel()
        assertEquals(1, out.size)
        assertEquals(GlassesPhotoProtocol.FA11_OP_CANCEL, out[0])
    }

    // ────────────────────────────────────────────────────────────────────
    // u32LeAt helper
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun u32LeAt_basic() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12, 0x99.toByte())
        assertEquals(0x12345678, GlassesPhotoProtocol.u32LeAt(bytes, 0))
    }

    @Test
    fun u32LeAt_offsetIntoBuffer() {
        // 0xFF prefix then 0x78 0x56 0x34 0x12
        val bytes = byteArrayOf(0xFF.toByte(), 0x78, 0x56, 0x34, 0x12)
        assertEquals(0x12345678, GlassesPhotoProtocol.u32LeAt(bytes, 1))
    }

    @Test
    fun u32LeAt_maxUnsignedValue() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(-1, GlassesPhotoProtocol.u32LeAt(bytes, 0))
    }

    // ────────────────────────────────────────────────────────────────────
    // 0x33 capture-command ack detector (smoke 22, 2026-09-15)
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun isCaptureAckSuccess_acceptsErrZero() {
        // 55 AA | seq | 33 | 02 (RESPONSE) | 01 00 (len=1) | 00 (err=0)
        val ack = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x07,                   // seq
            0x33,                   // cmd = aiPhotoBleCmd
            0x02,                   // type = RESPONSE
            0x01, 0x00,             // payload length u16 LE = 1
            0x00,                   // err = 0 → accepted
        )
        assertTrue(GlassesPhotoProtocol.isCaptureAckSuccess(ack))
    }

    @Test
    fun isCaptureAckSuccess_rejectsErrNonZero() {
        // err=0x01 means the firmware rejected (low battery / busy).
        val ack = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x07, 0x33, 0x02,
            0x01, 0x00,
            0x01,                   // err = 1 → rejected
        )
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(ack))
    }

    @Test
    fun isCaptureAckSuccess_rejectsRequestFrame() {
        // A 0x33 *request* frame has type=0x01 — not an ack.
        val request = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x07, 0x33, 0x01,         // type = REQUEST, not RESPONSE
            0x01, 0x00,
            0x50,
        )
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(request))
    }

    @Test
    fun isCaptureAckSuccess_rejectsWrongMagic() {
        // 66 BB instead of 55 AA → reject.
        val bad = byteArrayOf(
            0x66, 0xBB.toByte(),
            0x07, 0x33, 0x02,
            0x01, 0x00,
            0x00,
        )
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(bad))
    }

    @Test
    fun isCaptureAckSuccess_rejectsWrongPayloadLength() {
        // payload length u16 LE must equal 1, not 2.
        val bad = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x07, 0x33, 0x02,
            0x02, 0x00,             // len = 2 (not 1)
            0x00, 0x00,
        )
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(bad))
    }

    @Test
    fun isCaptureAckSuccess_rejectsOtherCmdBytes() {
        // 0x51 START status notification has the same shape but
        // different cmd — must not be misclassified as a 0x33 ack.
        val status = byteArrayOf(
            0x55, 0xAA.toByte(),
            0x07, 0x51, 0x02,         // cmd = 0x51, not 0x33
            0x01, 0x00,
            0x00,
        )
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(status))
    }

    @Test
    fun isCaptureAckSuccess_rejectsShortFrame() {
        // Less than 8 bytes → malformed, not an ack.
        val tooShort = byteArrayOf(0x55, 0xAA.toByte(), 0x07, 0x33, 0x02, 0x01)
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(tooShort))
    }
}
