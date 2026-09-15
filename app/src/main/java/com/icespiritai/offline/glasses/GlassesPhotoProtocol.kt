package com.icespiritai.offline.glasses

/**
 * Byte-level wire protocol for the smart-glasses BLE photo capture channel.
 *
 * Two transport layers coexist:
 *
 *  - **FFF0 management channel** ([buildCaptureRequestFrame] / [parseStatusNotify]):
 *    55 AA framed packets used for the AI-photo command (0x33) and the
 *    status stream (0x51). Sequence numbers and payload length are part of
 *    the frame; the parser validates them.
 *
 *  - **FA10 / FA11 / FA12 raw byte channels** ([parsePhotoChunk] /
 *    [buildFa11Resend] / [buildFa11Crc] / [buildFa11Cancel]):
 *    no 55 AA envelope. FA12 chunks are `[offset u32 LE | JPEG bytes]`.
 *    FA11 opcodes are 1-byte discriminator followed by 0–4 bytes of payload.
 *
 * Everything here is pure data + `ByteArray` math. No Android, no Bluetooth,
 * no coroutines, no `Context` — directly unit-testable on the JVM.
 *
 * Reference: `docs/glasses/AI识图传图提速_App连接参数配合.md` §6 BLE / FA10 协议细节.
 */
object GlassesPhotoProtocol {

    // ────────────────────────────────────────────────────────────────────
    // Frame-level constants (FFF0 management channel)
    // ────────────────────────────────────────────────────────────────────

    /** Magic word for FFF0 frames. Spec layout is `55 AA` then payload — bytes are `[0x55, 0xAA]` in transmission order. */
    const val FFF0_MAGIC_BYTE_0: Byte = 0x55
    const val FFF0_MAGIC_BYTE_1: Byte = 0xAA.toByte()

    /**
     * AI 识图拍照 command code (App → Glass, type=Request).
     *
     * **0x33 — verified against Glasses-A88 V2.4.5 (2026-09-15).**
     * Decompiling the OEM glasses app (`com.deepvision_tek.glass_front`
     * 3.1.00) revealed a second entry `aiPhotoRequestCmd = 0x50`
     * (`BluetoothController.requestAiPhotoCapture`) which we tried but
     * the user's firmware rejects with `55 aa 00 50 02 01 00 01`
     * (reject code 0x01). The user's V2.4.5 firmware only accepts the
     * older `aiPhotoBleCmd = 0x33`. Sticking with 0x33 — it's what the
     * firmware speaks. The OEM's 0x50 entry is firmware-version-gated
     * to newer revisions (not documented in `docs/glasses/`).
     */
    const val CMD_AI_CAPTURE: Byte = 0x33

    /** Status notify command code (Glass → App, type=Notify). */
    const val CMD_STATUS_NOTIFY: Byte = 0x51

    /** Request type (App-initiated). */
    const val TYPE_REQUEST: Byte = 0x01

    /** Response type (Glass-initiated reply to a request). */
    const val TYPE_RESPONSE: Byte = 0x02

    /** Notify type (Glass-initiated unsolicited event). */
    const val TYPE_NOTIFY: Byte = 0x04

    /** Legacy "FTP_READY" command code — pre-FA10 capture path; not used by the AI-photo flow. */
    const val CMD_LEGACY_FTP: Byte = 0x50

    // ────────────────────────────────────────────────────────────────────
    // 0x51 status payload values (first byte of the FFF0 payload)
    // ────────────────────────────────────────────────────────────────────

    const val STATUS_START: Byte = 0x01
    const val STATUS_SUCCESS: Byte = 0x02
    const val STATUS_FAILED: Byte = 0x03

    /** Legacy FTP_READY status — present in old firmware, ignored by the AI-photo flow. */
    const val STATUS_LEGACY_FTP_READY: Byte = 0x04

    // ────────────────────────────────────────────────────────────────────
    // FA11 control opcodes (App → Glass, raw write on FA11 char)
    // ────────────────────────────────────────────────────────────────────

    /** `02 | offset u32 LE` — request retransmission from `offset`. */
    const val FA11_OP_RESEND: Byte = 0x02

    /** `03 | crc32 u32 LE` — confirm receipt with CRC32 of the assembled JPEG. */
    const val FA11_OP_CRC: Byte = 0x03

    /** `04` — App-side abort / timeout cancellation. */
    const val FA11_OP_CANCEL: Byte = 0x04

    // ────────────────────────────────────────────────────────────────────
    // Defaults
    // ────────────────────────────────────────────────────────────────────

    /** Default JPEG quality parameter for the 0x33 capture command. Matches CLAUDE.md §"App 业务层". */
    const val DEFAULT_CAPTURE_QUALITY: Byte = 80

    /** FA12 chunk layout: first 4 bytes are JPEG offset u32 LE. */
    const val FA12_OFFSET_BYTES: Int = 4

    /** Lower bound on chunk payload size after the 4-byte offset prefix (a typical payload is ~240 B; defensive min so a degenerate peripheral doesn't produce 0-byte chunks). */
    const val FA12_MIN_DATA_BYTES: Int = 1

    // ────────────────────────────────────────────────────────────────────
    // Parsed-frame data classes
    // ────────────────────────────────────────────────────────────────────

    /**
     * Decoded 0x51 status payload.
     *
     * - [Start.fileSize] is `null` if the firmware omitted the 4-byte file
     *   size trailer after the status code (older or partial firmware).
     *   Callers should fall back to inferring the total size from the last
     *   FA12 chunk's `offset + data.size`.
     * - [LegacyFtpReady] is reported but **never** triggers any action —
     *   the AI-photo flow treats it as out-of-spec.
     * - [Failed.code] carries the first status byte verbatim (should be
     *   [STATUS_FAILED]=0x03 but spec doesn't guarantee exclusivity).
     */
    sealed class StatusNotify {
        data class Start(val fileSize: Int?) : StatusNotify()
        data object Success : StatusNotify()
        data class Failed(val code: Byte) : StatusNotify()
        data object LegacyFtpReady : StatusNotify()
    }

    /**
     * One FA12 photo chunk.
     *
     * - [offset] is the byte offset within the final JPEG where [data]
     *   should land (u32 LE on the wire).
     * - [data] is the raw JPEG bytes for this chunk — not validated
     *   against any format here; the [GlassesPhotoStream] enforces the
     *   assembly invariants.
     */
    data class PhotoChunk(val offset: Int, val data: ByteArray) {
        init {
            require(offset >= 0) { "chunk offset must be non-negative, was $offset" }
            require(data.isNotEmpty()) { "chunk data must be non-empty" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PhotoChunk) return false
            return offset == other.offset && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * offset + data.contentHashCode()
    }

    // ────────────────────────────────────────────────────────────────────
    // FFF0 frame builders / parsers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Build the FFF0 frame the App sends to issue a 0x33 AI-photo capture.
     *
     * Frame layout: `55 AA | seq | 33 | 01 | 01 00 | quality`.
     *
     * @param seq monotonically increasing sequence number; typically
     *   incremented per capture session. Glasses echo it in the 0x33
     *   response (we don't currently use the echo for demux — only one
     *   capture is in flight at a time per design D1).
     * @param quality JPEG quality byte the firmware should target (default 80).
     */
    fun buildCaptureRequestFrame(seq: Byte, quality: Byte = DEFAULT_CAPTURE_QUALITY): ByteArray {
        // 2 (magic) + 1 (seq) + 1 (cmd) + 1 (type) + 2 (len) + 1 (quality) = 8
        val frame = ByteArray(8)
        frame[0] = FFF0_MAGIC_BYTE_0
        frame[1] = FFF0_MAGIC_BYTE_1
        frame[2] = seq
        frame[3] = CMD_AI_CAPTURE
        frame[4] = TYPE_REQUEST
        frame[5] = 0x01                  // payload length u16 LE: low byte
        frame[6] = 0x00                  // payload length u16 LE: high byte
        frame[7] = quality
        return frame
    }

    /**
     * Detect a 0x33 capture-command acknowledgement from the firmware
     * (smoke 22 2026-09-15). Once the App sends `55 AA seq 33 01 01 00
     * quality`, the firmware responds with the **same** cmd byte but
     * type=0x02 (RESPONSE) and a 1-byte `err` payload: `0x00` accepted,
     * `0x01` rejected (typically "low battery" or "busy" — see doc §6.1).
     *
     * Once the firmware has acked the capture command it has also
     * committed to sending 0x51 START + FA12 chunks (or 0x51 FAILED on
     * capture-time error), so the ack is useful as a liveness marker in
     * logs.
     *
     * **It is not a transfer-completion signal and must not be treated
     * as one.** It arrives ~30 ms after the Request — before the glass
     * has taken the photo, let alone streamed a block. The collector
     * used to flip its SUCCESS flag on this frame, which made it
     * force-complete a still-empty buffer 3.5 s later and hand OCR a
     * JPEG padded with 0xFF ("拍照成功" but recognition always failed).
     * Only `0x51` SUCCESS / FAILED ends a transfer; see
     * [collectFa12Chunks] and
     * `docs/glasses/AI识图传图-App端接收处理说明.md` §2.3 Step 4.
     */
    fun isCaptureAckSuccess(payload: ByteArray): Boolean {
        if (payload.size != 8) return false
        if (payload[0] != FFF0_MAGIC_BYTE_0 || payload[1] != FFF0_MAGIC_BYTE_1) return false
        if (payload[3] != CMD_AI_CAPTURE) return false
        if (payload[4] != TYPE_RESPONSE) return false
        // payload length u16 LE == 1
        if (payload[5] != 0x01.toByte() || payload[6] != 0x00.toByte()) return false
        return payload[7] == 0x00.toByte()  // err = 0 → accepted
    }

    /**
     * Parse a raw FFF0 notify payload (the bytes the BLE stack delivers
     * after stripping the attribute handle / header) into a [StatusNotify].
     *
     * Returns `null` if the bytes don't form a valid FFF0 frame, the cmd
     * isn't 0x51, or the payload is malformed. Callers should treat `null`
     * as a protocol error and abort the capture.
     */
    fun parseStatusNotify(notifyPayload: ByteArray): StatusNotify? {
        val frame = parseFrame(notifyPayload) ?: return null
        if (frame.cmd != CMD_STATUS_NOTIFY) return null
        return parseStatusPayload(frame.payload)
    }

    /**
     * Low-level FFF0 frame parser. Validates magic + length. Returns `null`
     * on any structural issue (bad magic, length mismatch, truncated buffer).
     */
    private fun parseFrame(bytes: ByteArray): DecodedFrame? {
        if (bytes.size < 7) return null
        if (bytes[0] != FFF0_MAGIC_BYTE_0 || bytes[1] != FFF0_MAGIC_BYTE_1) return null
        val seq = bytes[2]
        val cmd = bytes[3]
        val type = bytes[4]
        val lenLo = bytes[5].toInt() and 0xFF
        val lenHi = bytes[6].toInt() and 0xFF
        val payloadLen = lenLo or (lenHi shl 8)
        if (bytes.size != 7 + payloadLen) return null
        val payload = if (payloadLen == 0) ByteArray(0) else bytes.copyOfRange(7, 7 + payloadLen)
        return DecodedFrame(seq, cmd, type, payload)
    }

    private data class DecodedFrame(
        val seq: Byte,
        val cmd: Byte,
        val type: Byte,
        val payload: ByteArray,
    )

    private fun parseStatusPayload(payload: ByteArray): StatusNotify? {
        if (payload.isEmpty()) return null
        return when (payload[0]) {
            STATUS_START -> {
                // Optional 4-byte file size trailer
                if (payload.size == 1) {
                    StatusNotify.Start(fileSize = null)
                } else if (payload.size == 1 + 4) {
                    val size = u32LeAt(payload, 1)
                    StatusNotify.Start(fileSize = size)
                } else {
                    // Malformed START length
                    null
                }
            }

            STATUS_SUCCESS -> StatusNotify.Success
            STATUS_FAILED -> StatusNotify.Failed(payload[0])
            STATUS_LEGACY_FTP_READY -> StatusNotify.LegacyFtpReady
            else -> null
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // FA10 / FA11 / FA12 raw byte builders / parsers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Parse a FA12 notify payload into a [PhotoChunk].
     *
     * Layout: `offset u32 LE | JPEG data bytes` (no 55 AA envelope).
     *
     * Returns `null` if the payload is shorter than `FA12_OFFSET_BYTES +
     * FA12_MIN_DATA_BYTES` (degenerate zero-byte chunk).
     */
    fun parsePhotoChunk(fa12Payload: ByteArray): PhotoChunk? {
        if (fa12Payload.size < FA12_OFFSET_BYTES + FA12_MIN_DATA_BYTES) return null
        val offset = u32LeAt(fa12Payload, 0)
        // ByteArray.copyOfRange does not retain a reference to the source
        // — important here because `fa12Payload` may be reused by the
        // Bluetooth stack for the next chunk.
        val data = fa12Payload.copyOfRange(FA12_OFFSET_BYTES, fa12Payload.size)
        return PhotoChunk(offset, data)
    }

    /**
     * Build the FA11 op2 resend request.
     *
     * Layout: `02 | offset u32 LE` (5 bytes total). Glasses should
     * retransmit from `offset` onwards.
     */
    fun buildFa11Resend(offset: Int): ByteArray {
        require(offset >= 0) { "resend offset must be non-negative, was $offset" }
        val out = ByteArray(1 + 4)
        out[0] = FA11_OP_RESEND
        putU32Le(out, 1, offset.toLong() and 0xFFFFFFFFL)
        return out
    }

    /**
     * Build the FA11 op3 receipt-confirmation.
     *
     * Layout: `03 | crc32 u32 LE` (5 bytes total). The CRC is computed by
     * the App over the fully assembled JPEG (see [GlassesPhotoStream.crc32])
     * and the glasses verify before sending 0x51 SUCCESS.
     */
    fun buildFa11Crc(crc32: Long): ByteArray {
        require(crc32 >= 0) { "crc32 must be non-negative, was $crc32" }
        val out = ByteArray(1 + 4)
        out[0] = FA11_OP_CRC
        putU32Le(out, 1, crc32 and 0xFFFFFFFFL)
        return out
    }

    /**
     * Build the FA11 op4 cancel/abort.
     *
     * Layout: `04` (1 byte). Sent when the App-side pipeline times out or
     * the user cancels mid-capture.
     */
    fun buildFa11Cancel(): ByteArray = byteArrayOf(FA11_OP_CANCEL)

    // ────────────────────────────────────────────────────────────────────
    // Little-endian u32 helpers (extracted so unit tests cover them too)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Read an unsigned 32-bit little-endian value from [bytes] starting at
     * [offset]. Bytes are interpreted as unsigned, result is in
     * `[0, 2^32)`. Returns `Int` rather than `Long` because no spec field
     * approaches 2 GB and staying in `Int` keeps the FA10 chunk-offset /
     * file-size parsing simple.
     */
    fun u32LeAt(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun putU32Le(out: ByteArray, offset: Int, value: Long) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        out[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
