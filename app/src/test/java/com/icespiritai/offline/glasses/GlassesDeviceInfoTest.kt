package com.icespiritai.offline.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device-info read: the six `0x10` sub-commands that decide whether these
 * glasses can do Wi-Fi/FTP media sync or would have to be driven over
 * SPP/RFCOMM, plus the decoding of whatever they answer.
 *
 * Two independent sources agree on every layout here:
 *  - the OEM APK (`com.deepvision_tek.glass_front` 3.1.00):
 *    `BleCommandConfig$Companion.default()` for the sub-command bytes and
 *    `BluetoothController.handleReceivedData` for the field shapes;
 *  - a real capture from our own glasses (2026-09-17, V2.4.6), kept verbatim
 *    in [decodesTheRealCaptureFromOurGlasses] so a future firmware change
 *    fails this test instead of silently decoding nonsense.
 */
class GlassesDeviceInfoTest {

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

    private fun tlv(type: Int, vararg value: Int): ByteArray =
        byteArrayOf(type.toByte(), value.size.toByte()) +
            ByteArray(value.size) { value[it].toByte() }

    // -- Requests -------------------------------------------------------

    @Test
    fun deviceInfoRequestUsesTheSameEnvelopeForEveryField() {
        // `55 AA | 41 | 10 | 01 | 02 00 | F4 00` — memory.
        assertArrayEquals(
            byteArrayOf(
                0x55, 0xAA.toByte(),
                0x41,
                0x10,           // cmd = getDeviceInfoCmd
                0x01,           // type = Request
                0x02, 0x00,     // payload length u16 LE
                0xF4.toByte(),  // sub-command = memory
                0x00,           // TLV length 0 -> "report it"
            ),
            GlassesPhotoProtocol.buildDeviceInfoRequestFrame(
                seq = 0x41,
                subCmd = GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )

        // The firmware read is the same builder with sub 0x20.
        assertArrayEquals(
            GlassesPhotoProtocol.buildDeviceInfoRequestFrame(
                seq = 0x41,
                subCmd = GlassesPhotoProtocol.SUB_FIRMWARE_INFO,
            ),
            GlassesPhotoProtocol.buildFirmwareVersionRequestFrame(seq = 0x41),
        )
    }

    @Test
    fun everySubCommandMatchesTheVendorConfig() {
        assertEquals(0x20, GlassesDeviceInfo.Field.FIRMWARE.subCmd.toInt())
        assertEquals(0xF4, GlassesDeviceInfo.Field.MEMORY.subCmd.toInt() and 0xFF)
        assertEquals(0x17, GlassesDeviceInfo.Field.FILES.subCmd.toInt())
        assertEquals(0xF3, GlassesDeviceInfo.Field.FTP.subCmd.toInt() and 0xFF)
        assertEquals(0xF2, GlassesDeviceInfo.Field.P2P.subCmd.toInt() and 0xFF)
        assertEquals(0x14, GlassesDeviceInfo.Field.AP_ACCOUNT.subCmd.toInt())
        assertEquals(
            listOf("firmware", "memory", "files", "ftp", "p2p", "apAccount"),
            GlassesDeviceInfo.Field.entries.map { it.label },
        )
    }

    // -- Answers --------------------------------------------------------

    @Test
    fun readsValueFromADeviceInfoResponse() {
        val frame = fff0(
            seq = 0x42,
            cmd = 0x10,
            type = 0x02,
            payload = tlv(
                0xF4,
                0xDF, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
        )

        assertArrayEquals(
            byteArrayOf(
                0xDF.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
            GlassesPhotoProtocol.deviceInfoValue(frame, GlassesPhotoProtocol.SUB_MEMORY),
        )
    }

    @Test
    fun readsValueFromADeviceStatusNotifyTlvList() {
        // Real V2.4.6 frame: `55 AA | 15 | 11 | 03 | 03 00 | 17 01 00`
        // (the file count also rides along in the unprompted notifies).
        val payload = tlv(0x17, 0x00)
        val frame = fff0(seq = 0x15, cmd = 0x11, type = 0x03, payload = payload)

        assertArrayEquals(
            byteArrayOf(0x00),
            GlassesPhotoProtocol.deviceInfoValue(frame, GlassesPhotoProtocol.SUB_FILE_COUNT),
        )
        // Not present in this notify -> the caller keeps waiting.
        assertNull(GlassesPhotoProtocol.deviceInfoValue(frame, GlassesPhotoProtocol.SUB_MEMORY))
    }

    @Test
    fun ignoresFramesThatCannotBeAnAnswer() {
        // Wrong command (0x51 capture status).
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                fff0(seq = 0x01, cmd = 0x51, type = 0x04, payload = byteArrayOf(0x02)),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
        // A 0x10 response carrying a different field.
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = tlv(0x17, 0x00)),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
        // Zero-length value is an absence, not an empty value.
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = tlv(0xF4)),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
        // Truncated TLV: declares 4 bytes, carries 2.
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                fff0(
                    seq = 0x01,
                    cmd = 0x10,
                    type = 0x02,
                    payload = byteArrayOf(0xF4.toByte(), 0x04, 0x00, 0x40),
                ),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
        // Truncated list: the walk stops instead of reading payload bytes as
        // a header, so a later 0xF4 is not stolen from a broken boundary.
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                fff0(
                    seq = 0x02,
                    cmd = 0x11,
                    type = 0x03,
                    payload = byteArrayOf(0x01, 0x05, 0x64, 0xF4.toByte(), 0x01, 0x00),
                ),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
        // Bad magic.
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                byteArrayOf(0x00, 0x00, 0x01, 0x10, 0x02, 0x04, 0x00, 0xF4.toByte(), 0x04, 0x01, 0x02, 0x03),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
        // Payload too short to hold a TLV header at all.
        assertNull(
            GlassesPhotoProtocol.deviceInfoValue(
                fff0(seq = 0x01, cmd = 0x10, type = 0x02, payload = byteArrayOf(0xF4.toByte())),
                GlassesPhotoProtocol.SUB_MEMORY,
            ),
        )
    }

    // -- Decoding -------------------------------------------------------

    /**
     * The six answers our glasses actually sent on 2026-09-17 (V2.4.6),
     * pushed through the parser exactly as `readDeviceInfo` feeds them.
     */
    @Test
    fun decodesTheRealCaptureFromOurGlasses() {
        val frames = mapOf(
            "firmware" to "55aa4110020800200656322e342e36",
            "memory" to "55aa4210021200f410df010000000000000400000000000000",
            "files" to "55aa43100208001706000411000000",
            "ftp" to "55aa4410020600f30400000000",
            "p2p" to "55aa4510020800f206c41222556008",
            "apAccount" to "55aa4610020600140401000200",
        ).mapValues { (_, hex) ->
            ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

        val answers = GlassesDeviceInfo.Field.entries
            .mapNotNull { field ->
                val frame = frames[field.label] ?: return@mapNotNull null
                GlassesPhotoProtocol.deviceInfoValue(frame, field.subCmd)?.let { field.label to it }
            }
            .toMap()
        val info = GlassesDeviceInfo.fromAnswers(answers)

        assertEquals("V2.4.6", info.firmwareVersion)
        assertEquals(479L, info.usedStorageMb)
        assertEquals(4L, info.totalStorageMb)
        assertEquals(17, info.unsyncedFiles)
        // AP mode was not running: the FTP address is all zero bytes, which is
        // "no address", not 0.0.0.0.
        assertNull(info.ftpIp)
        assertEquals("C4:12:22:55:60:08", info.p2pMac)
        assertNull(info.apSsid)
        assertNull(info.apPassword)
        assertEquals(4, info.apAccountBytes)

        // The answer to "which photo path does this hardware belong to?":
        // the OEM app sees reported storage and a P2P rendezvous, so it is a
        // storage device — SPP/RFCOMM is the *memoryless* fallback, not us.
        assertEquals(false, info.memoryless)
        assertTrue(info.hasUsableStorage)
        assertTrue(info.offersWifiTransfer)

        assertEquals("df01000000000000", info.rawHex["memory"]?.substring(0, 16))
        assertEquals("c41222556008", info.rawHex["p2p"])
    }

    @Test
    fun memoryIsTwoLittleEndianU64s() {
        // 0x1DF = 479 used, 4 total — the shape the OEM app reads (offset 0
        // and offset 8, eight bytes each).
        val info = GlassesDeviceInfo.fromAnswers(
            mapOf(
                "memory" to byteArrayOf(
                    0xDF.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                ),
            ),
        )

        assertEquals(479L, info.usedStorageMb)
        assertEquals(4L, info.totalStorageMb)
        assertTrue(info.storageInfoKnown)
        assertEquals(false, info.memoryless)
        assertTrue(info.hasUsableStorage)
    }

    @Test
    fun memorylessMeansNothingReportedUsed() {
        val empty = GlassesDeviceInfo.fromAnswers(mapOf("memory" to ByteArray(16)))

        assertEquals(0L, empty.usedStorageMb)
        assertEquals(0L, empty.totalStorageMb)
        // The OEM's `isMemoryless` test: this is the device it drives over SPP.
        assertEquals(true, empty.memoryless)
        assertFalse(empty.hasUsableStorage)
    }

    @Test
    fun aTruncatedMemoryAnswerStaysUnknownInsteadOfBeingGuessed() {
        // Eight bytes is one u64: half an answer is not an answer.
        val half = GlassesDeviceInfo.fromAnswers(
            mapOf("memory" to byteArrayOf(0xDF.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
        )

        assertNull(half.usedStorageMb)
        assertNull(half.totalStorageMb)
        assertNull(half.memoryless)
        assertFalse(half.storageInfoKnown)
        assertFalse(half.hasUsableStorage)
        assertEquals("df01000000000000", half.rawHex["memory"])
    }

    @Test
    fun unansweredFieldsStayUnknown() {
        val info = GlassesDeviceInfo.fromAnswers(emptyMap())

        assertNull(info.firmwareVersion)
        assertNull(info.usedStorageMb)
        assertNull(info.totalStorageMb)
        assertNull(info.unsyncedFiles)
        assertNull(info.ftpIp)
        assertNull(info.p2pMac)
        assertNull(info.apSsid)
        // "We never got an answer" is not "memoryless": that distinction is
        // what stops a silent firmware from being blamed for a missing photo
        // path it may well have.
        assertNull(info.memoryless)
        assertFalse(info.hasUsableStorage)
        assertFalse(info.offersWifiTransfer)
    }

    @Test
    fun fileCountComesInTwoShapes() {
        // `0x10` read: `[4 bytes][u32 LE count]` — real capture `00 04 11 00 00 00`.
        assertEquals(17, GlassesDeviceInfo.fileCount(byteArrayOf(0x00, 0x04, 0x11, 0x00, 0x00, 0x00)))
        assertEquals(0, GlassesDeviceInfo.fileCount(byteArrayOf(0x00, 0x04, 0x00, 0x00, 0x00, 0x00)))
        // `0x11` notify: a single byte (real frame `... 17 01 00`).
        assertEquals(0, GlassesDeviceInfo.fileCount(byteArrayOf(0x00)))
        assertEquals(5, GlassesDeviceInfo.fileCount(byteArrayOf(0x05)))
        // Anything else is not a count.
        assertNull(GlassesDeviceInfo.fileCount(byteArrayOf(0x01, 0x02)))
        assertNull(GlassesDeviceInfo.fileCount(ByteArray(0)))
    }

    @Test
    fun ftpAddressIsAReversedDottedQuad() {
        // 192.168.43.1 arrives as 01 2B A8 C0 -> reversed -> 192.168.43.1.
        assertEquals(
            "192.168.43.1",
            GlassesDeviceInfo.ftpAddress(byteArrayOf(0x01, 0x2B, 0xA8.toByte(), 0xC0.toByte())),
        )
        // All zero = AP mode is not up.
        assertNull(GlassesDeviceInfo.ftpAddress(byteArrayOf(0x00, 0x00, 0x00, 0x00)))
        // A printable form is passed through (other firmware paths).
        assertEquals(
            "10.0.0.1",
            GlassesDeviceInfo.ftpAddress("10.0.0.1\u0000".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun apAccountIsAMarkerLengthList() {
        // `01 04 ssid 02 06 passwd` — marker 1 = SSID, marker 2 = password.
        val bytes = byteArrayOf(0x01, 0x04) + "GFAP".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x02, 0x06) + "123456".toByteArray(Charsets.UTF_8)
        assertEquals("GFAP" to "123456", GlassesDeviceInfo.apAccount(bytes))

        // Real capture `01 00 02 00`: both entries empty -> no AP session yet.
        assertEquals(null to null, GlassesDeviceInfo.apAccount(byteArrayOf(0x01, 0x00, 0x02, 0x00)))

        // A truncated entry stops the walk instead of reading past the end.
        val truncated = byteArrayOf(0x01, 0x04) + "GFAP".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x02, 0x09, 0x31)
        assertEquals("GFAP" to null, GlassesDeviceInfo.apAccount(truncated))
    }

    @Test
    fun textAndMacFieldsRejectBinaryNoise() {
        assertNull(GlassesDeviceInfo.printableText(byteArrayOf(0x00, 0x00, 0x00, 0x00)))
        assertNull(GlassesDeviceInfo.printableText("  ".toByteArray(Charsets.UTF_8)))
        assertEquals(
            "10.0.0.1",
            GlassesDeviceInfo.printableText("10.0.0.1\u0000".toByteArray(Charsets.UTF_8)),
        )

        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            GlassesDeviceInfo.macAddress(
                byteArrayOf(
                    0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
                    0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
                ),
            ),
        )
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            GlassesDeviceInfo.macAddress("AA:BB:CC:DD:EE:FF".toByteArray(Charsets.UTF_8)),
        )
        assertNull(GlassesDeviceInfo.macAddress(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun littleEndianWidthsAndOffsets() {
        assertEquals(0x7FL, GlassesDeviceInfo.littleEndianLong(byteArrayOf(0x7F)))
        assertEquals(0x1234L, GlassesDeviceInfo.littleEndianLong(byteArrayOf(0x34, 0x12)))
        assertEquals(
            0x1234_5678L,
            GlassesDeviceInfo.littleEndianLong(byteArrayOf(0x78, 0x56, 0x34, 0x12)),
        )
        // Offset + length read, the way the two u64s are taken out of the
        // memory answer.
        val pair = byteArrayOf(0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05, 0x00)
        assertEquals(0x201L, GlassesDeviceInfo.littleEndianLong(pair, 0, 8))
        assertEquals(5L, GlassesDeviceInfo.littleEndianLong(pair, 8, 2))
        // A signed-looking byte still decodes as unsigned.
        assertEquals(0xFFL, GlassesDeviceInfo.littleEndianLong(byteArrayOf(0xFF.toByte())))
        assertNull(GlassesDeviceInfo.littleEndianLong(byteArrayOf()))
        assertNull(GlassesDeviceInfo.littleEndianLong(byteArrayOf(0x01, 0x02, 0x03)))
        // Out of bounds is a refusal, not a partial read.
        assertNull(GlassesDeviceInfo.littleEndianLong(byteArrayOf(0x01, 0x02), 1, 4))
    }

    @Test
    fun nulPaddedFirmwareStringIsTrimmed() {
        assertEquals(
            "V2.4.6",
            GlassesDeviceInfo.firmwareString("V2.4.6\u0000\u0000".toByteArray(Charsets.UTF_8)),
        )
        assertEquals("V2.5.8", GlassesDeviceInfo.firmwareString("V2.5.8".toByteArray(Charsets.UTF_8)))
        assertNull(GlassesDeviceInfo.firmwareString(ByteArray(0)))
    }
}
