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

    /**
     * Device-info command code (App → Glass, type=Request) — the TLV
     * envelope for "read one field" sub-commands.
     *
     * Verified twice: the OEM app's `BleCommandConfig.getDeviceInfoCmd = 0x10`
     * (`com.deepvision_tek.glass_front` 3.1.00, `BleCommandConfig$Companion.default()`
     * constructor args) and the vendor's reference project, which does the
     * same read (`docs/glasses/官方技术给的示例（仅参考）/.../BluetoothController.kt`
     * `requestFirmwareVersion()` → `buildFirmwareVersionRequestPacket`).
     */
    const val CMD_DEVICE_INFO: Byte = 0x10

    /**
     * Sub-command: firmware version, a UTF-8 string in a `[type][len][value]`
     * TLV. Answered in a `0x10` Response and mirrored inside `0x11` device
     * status notifies (vendor reference `handleDeviceInfoPayload` /
     * `handleDeviceStatusNotify`).
     */
    const val SUB_FIRMWARE_INFO: Byte = 0x20

    /** Device-status command code (Glass → App) whose TLV list may carry the firmware version. */
    const val CMD_DEVICE_STATUS: Byte = 0x11

    /**
     * Wi-Fi on/off (App → Glass, one payload byte: [WIFI_ON_PAYLOAD] /
     * [WIFI_OFF_PAYLOAD]).
     *
     * OEM `BleCommandConfig.cmdOpenWifi = 54`, `openWifi(true)` builds exactly
     * this frame (`BluetoothController.openWifi`). Used by the Wi-Fi media
     * transfer experiment — see [P2P_START_PAYLOAD].
     */
    const val CMD_OPEN_WIFI: Byte = 0x36

    /** Payload of `0x36`: turn the glasses' Wi-Fi on. */
    const val WIFI_ON_PAYLOAD: Byte = 0x01

    /** Payload of `0x36`: turn it off again. */
    const val WIFI_OFF_PAYLOAD: Byte = 0x00

    /**
     * P2P / AP mode switch (App → Glass, one payload byte).
     *
     * OEM `BleCommandConfig.cmdStartP2p = 57`. The payload picks the mode:
     * [P2P_START_PAYLOAD] starts Wi-Fi Direct (`startP2pMode`), while the OEM's
     * `startTransferApMode` and `stopP2pMode` both send `0x00` — so this
     * command's payload domain is 2 = P2P, 0 = "AP/off", and there is no
     * third value to discover.
     */
    const val CMD_START_P2P: Byte = 0x39

    /** Payload of `0x39`: start Wi-Fi Direct mode. */
    const val P2P_START_PAYLOAD: Byte = 0x02

    /** Payload of `0x39`: transfer-AP mode / stop P2P (OEM uses `0x00` for both). */
    const val P2P_STOP_PAYLOAD: Byte = 0x00

    /** `0x11` TLV: battery level. */
    const val SUB_BATTERY: Byte = 0x01

    /** `0x11` TLV: the glasses started/stopped recording video on their own. */
    const val SUB_MEDIA_VIDEO_STATUS: Byte = 0x0B

    /** `0x11` TLV: the glasses started/stopped recording audio on their own. */
    const val SUB_MEDIA_AUDIO_STATUS: Byte = 0x0C

    /**
     * `0x11` TLV: whether BT network sharing (PAN) is up. `0` means "on" —
     * the firmware's boolean convention (OEM `parseDeviceStatusNotifyPayload`
     * inverts it).
     */
    const val SUB_BT_NETWORK_SHARING: Byte = 0x15

    /**
     * `0x11` TLV: **the glasses took a photo** (hardware shutter button or
     * device-side capture). One byte, `0` = success.
     *
     * This is the event our App was throwing away: the firmware does report
     * the shutter press, but `parseFirmwareVersion` only looked at the
     * firmware TLV, so nothing reacted (user report 2026-09-17, real frame
     * `55aa15 11 03 0300 17 01 00` captured on the device).
     * OEM equivalent: `BleCommandConfig.mediaPhotoResult` (23).
     */
    const val SUB_MEDIA_PHOTO_RESULT: Byte = 0x17

    // ── Device-info sub-commands (`0x10` Request → `0x10` Response) ──────
    // Values taken from the OEM app's `BleCommandConfig$Companion.default()`
    // and mirrored in the APK's `assets/app_config.json` (`bleProtocol.*`).

    /** AP-mode account/credential blob (Wi-Fi transfer path). */
    const val SUB_AP_ACCOUNT: Byte = 0x14

    /**
     * Number of files waiting to be synced. The glasses also push this
     * unprompted as a `0x11` TLV (real frame `…11 03 0300 17 01 00` = count 0).
     */
    const val SUB_FILE_COUNT: Byte = 0x17

    /** Wi-Fi Direct (P2P) MAC of the glasses — the media-sync rendezvous. */
    const val SUB_P2P_MAC: Byte = 0xF2.toByte()

    /** FTP server IP the glasses expose in AP/P2P mode (`downloadPhotoFromFtp`). */
    const val SUB_FTP_IP: Byte = 0xF3.toByte()

    /**
     * Storage information. The OEM app treats "no storage" as *memoryless*
     * and switches its photo path from FTP-over-Wi-Fi to SPP/RFCOMM
     * (`isMemorylessDevice()` → 「开始普通拍照，走 SPP/GFSP 传输」).
     * Reading it is how we find out which world our glasses live in.
     */
    const val SUB_MEMORY: Byte = 0xF4.toByte()

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
        return captureAckError(payload) == 0
    }

    /**
     * Error byte of a `0x33` Response, or `null` when [payload] is not one.
     *
     * `0` = accepted; anything else = rejected (low battery, OTA in
     * progress, _or simply busy taking the photo the wearer just triggered
     * with the shutter button_). The OEM retries once after 400 ms on a
     * rejection, which is exactly the case the button watcher hits.
     */
    fun captureAckError(payload: ByteArray): Int? {
        if (payload.size != 8) return null
        if (payload[0] != FFF0_MAGIC_BYTE_0 || payload[1] != FFF0_MAGIC_BYTE_1) return null
        if (payload[3] != CMD_AI_CAPTURE) return null
        if (payload[4] != TYPE_RESPONSE) return null
        // payload length u16 LE == 1
        if (payload[5] != 0x01.toByte() || payload[6] != 0x00.toByte()) return null
        return payload[7].toInt() and 0xFF
    }

    // ────────────────────────────────────────────────────────────────────
    // Firmware version (0x10 device-info read, sub-command 0x20)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Build the frame that asks the glasses for their firmware version:
     * `55 AA | seq | 10 | 01 | 02 00 | 20 00`.
     *
     * Payload is a zero-length `[type=0x20][len=0]` TLV, exactly like the
     * vendor reference (`BlePacketBuilder.buildFirmwareVersionRequestPacket`
     * → `buildSubCommandPacket(seq, subFirmwareInfo)`). Read-only: the
     * glasses answer with a `0x10` Response and also mirror the TLV in
     * `0x11` status notifies, so a version read is safe to do at any time.
     */
    fun buildFirmwareVersionRequestFrame(seq: Byte): ByteArray =
        buildDeviceInfoRequestFrame(seq, SUB_FIRMWARE_INFO)

    /**
     * Ask for one device-info field: `55 AA | seq | 10 | 01 | 02 00 | <sub> 00`.
     *
     * The OEM app uses this envelope for every field it reads (firmware
     * version, battery, memory, file count, FTP IP, P2P MAC, AP account), and
     * answers arrive either as a `0x10` Response or inside a `0x11` status
     * notify — see [deviceInfoValue].
     */
    fun buildDeviceInfoRequestFrame(seq: Byte, subCmd: Byte): ByteArray = buildFrame(
        seq = seq,
        cmd = CMD_DEVICE_INFO,
        type = TYPE_REQUEST,
        payload = byteArrayOf(subCmd, 0x00),
    )

    /**
     * A plain Request frame with an arbitrary payload:
     * `55 AA | seq | cmd | 01 | <len u16 LE> | payload`.
     *
     * The OTA write ([buildOtaFrame]-style URLs) and the device-info reads
     * each have their own shape; this is the raw builder they are special
     * cases of, needed for the one-byte switches ([CMD_OPEN_WIFI],
     * [CMD_START_P2P]).
     */
    fun buildRequestFrame(seq: Byte, cmd: Byte, payload: ByteArray): ByteArray =
        buildFrame(seq, cmd, TYPE_REQUEST, payload)

    /** Request frame whose payload is a single byte (Wi-Fi / P2P switches). */
    fun buildSwitchRequestFrame(seq: Byte, cmd: Byte, value: Byte): ByteArray =
        buildRequestFrame(seq, cmd, byteArrayOf(value))

    /**
     * Value of [subType] from a `0x10` Response (TLV at offset 0) or from the
     * TLV list of a `0x11` status notify, whichever the firmware chose to
     * answer with. `null` when the frame carries no such field.
     *
     * Deliberately format-agnostic: the same sub-command answers with a
     * UTF-8 string (IP), a little-endian integer (memory/file count) or a raw
     * byte blob (P2P MAC) depending on the field, and guessing wrong is worse
     * than handing the caller the bytes (see `GlassesDeviceInfo`).
     */
    fun deviceInfoValue(frame: ByteArray, subType: Byte): ByteArray? {
        val decoded = parseFrame(frame) ?: return null
        val type = subType.toInt() and 0xFF
        return when (decoded.cmd) {
            CMD_DEVICE_INFO -> tlvRawValueAt(decoded.payload, 0, type)
            CMD_DEVICE_STATUS -> tlvRawValueInList(decoded.payload, type)
            else -> null
        }
    }

    /**
     * Is [frame] an unsolicited `0x11` device-status notify?
     *
     * Matters because the two channels reuse TLV numbers: `0x17` is the
     * *file count* in a `0x10` answer but the *photo-result event* in a
     * `0x11` notify (OEM `subFileCount` = `mediaPhotoResult` = 23), so a
     * caller reading the count must not take a shutter notification as
     * "0 files pending".
     */
    fun isDeviceStatusNotify(frame: ByteArray): Boolean =
        parseFrame(frame)?.cmd == CMD_DEVICE_STATUS

    /**
     * Extract the firmware version from a raw FFF0 frame — either the `0x10`
     * Response to [buildFirmwareVersionRequestFrame] (payload is the
     * `[0x20][len][utf8]` TLV) or a `0x11` device-status notify (payload is a
     * list of TLVs, the version being one of them).
     *
     * Returns `null` when the frame isn't a valid FFF0 frame, isn't one of
     * those two commands, or carries no non-blank version — callers keep
     * waiting for a later frame in that case, because V2.4.5 also pushes
     * status notifies unprompted.
     */
    fun parseFirmwareVersion(frame: ByteArray): String? {
        val decoded = parseFrame(frame) ?: return null
        return when (decoded.cmd) {
            CMD_DEVICE_INFO -> firmwareFromTlvAt(decoded.payload, 0)
            CMD_DEVICE_STATUS -> firmwareFromTlvList(decoded.payload)
            else -> null
        }
    }

    /** Is this frame a `0x10` Response carrying a firmware-version TLV? */
    fun isFirmwareVersionResponse(frame: ByteArray): Boolean {
        val decoded = parseFrame(frame) ?: return false
        if (decoded.cmd != CMD_DEVICE_INFO) return false
        if (decoded.type != TYPE_RESPONSE) return false
        return firmwareFromTlvAt(decoded.payload, 0) != null
    }

    /**
     * One entry from a `0x11` device-status notify: `[type][len][value…]`.
     *
     * [flag] decodes the firmware's 1-byte booleans: **`0` means true**
     * (OEM `parseDeviceStatusNotifyPayload` does the same inversion for the
     * PAN and photo-result TLVs), so `null` means "this TLV isn't a flag".
     */
    data class DeviceStatusTlv(val type: Int, val value: ByteArray) {
        val flag: Boolean? get() = if (value.size == 1) value[0].toInt() == 0 else null

        override fun equals(other: Any?): Boolean =
            other is DeviceStatusTlv && type == other.type && value.contentEquals(other.value)

        override fun hashCode(): Int = 31 * type + value.contentHashCode()
    }

    /**
     * Decode the TLV list carried by a `0x11` device-status notify.
     *
     * Returns an empty list for any frame that is not a valid `0x11` frame.
     * A malformed TLV stops the walk (same rule as the firmware-version
     * parser): reading past a broken length would turn payload bytes into
     * fake headers.
     */
    fun parseDeviceStatusTlvs(frame: ByteArray): List<DeviceStatusTlv> {
        val decoded = parseFrame(frame) ?: return emptyList()
        if (decoded.cmd != CMD_DEVICE_STATUS) return emptyList()
        val payload = decoded.payload
        val out = ArrayList<DeviceStatusTlv>(4)
        var offset = 0
        while (offset + 2 <= payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val len = payload[offset + 1].toInt() and 0xFF
            if (offset + 2 + len > payload.size) return out
            out += DeviceStatusTlv(type, payload.copyOfRange(offset + 2, offset + 2 + len))
            offset += 2 + len
        }
        return out
    }

    /**
     * Did the glasses just take a photo on their own (hardware shutter)?
     *
     * True for a `0x11` frame whose `mediaPhotoResult` TLV reports success
     * (`17 01 00`). Deliberately does not require anything else in the frame:
     * V2.5.8 packs the photo result together with other TLVs.
     */
    fun reportsShutterPhoto(frame: ByteArray): Boolean =
        parseDeviceStatusTlvs(frame).any {
            it.type == (SUB_MEDIA_PHOTO_RESULT.toInt() and 0xFF) && it.flag == true
        }

    /** BT network-sharing (PAN) state as reported by the glasses, or `null`. */
    fun reportsNetworkSharingOn(frame: ByteArray): Boolean? =
        parseDeviceStatusTlvs(frame)
            .firstOrNull { it.type == (SUB_BT_NETWORK_SHARING.toInt() and 0xFF) }
            ?.flag

    /**
     * `[type][len][value…]` at [offset], or `null` if this isn't that TLV,
     * declares zero length, or runs past the payload.
     */
    private fun tlvRawValueAt(payload: ByteArray, offset: Int, type: Int): ByteArray? {
        if (offset + 2 > payload.size) return null
        if ((payload[offset].toInt() and 0xFF) != type) return null
        val len = payload[offset + 1].toInt() and 0xFF
        if (len == 0 || offset + 2 + len > payload.size) return null
        return payload.copyOfRange(offset + 2, offset + 2 + len)
    }

    /**
     * First [type] entry of a `[type][len][value]…` list, or `null`.
     *
     * A malformed length stops the walk with `null` instead of skipping
     * ahead: reading past a broken boundary would turn payload bytes into
     * fake headers (same rule as [parseDeviceStatusTlvs]).
     */
    private fun tlvRawValueInList(payload: ByteArray, type: Int): ByteArray? {
        var offset = 0
        while (offset + 2 <= payload.size) {
            val len = payload[offset + 1].toInt() and 0xFF
            if (offset + 2 + len > payload.size) return null
            if ((payload[offset].toInt() and 0xFF) == type) {
                return tlvRawValueAt(payload, offset, type)
            }
            offset += 2 + len
        }
        return null
    }

    /** `[0x20][len][utf8…]` at [offset], or `null` if this isn't that TLV. */
    private fun firmwareFromTlvAt(payload: ByteArray, offset: Int): String? =
        tlvRawValueAt(payload, offset, SUB_FIRMWARE_INFO.toInt() and 0xFF)
            ?.toString(Charsets.UTF_8)
            ?.trim { it <= ' ' || it == '\u0000' }
            ?.takeIf { it.isNotBlank() }

    /** Walk a `[type][len][value]…` list and return the 0x20 entry, if any. */
    private fun firmwareFromTlvList(payload: ByteArray): String? =
        tlvRawValueInList(payload, SUB_FIRMWARE_INFO.toInt() and 0xFF)
            ?.toString(Charsets.UTF_8)
            ?.trim { it <= ' ' || it == '\u0000' }
            ?.takeIf { it.isNotBlank() }

    /** Generic FFF0 frame builder used by the version read (and future OTA frames). */
    private fun buildFrame(seq: Byte, cmd: Byte, type: Byte, payload: ByteArray): ByteArray {
        val frame = ByteArray(7 + payload.size)
        frame[0] = FFF0_MAGIC_BYTE_0
        frame[1] = FFF0_MAGIC_BYTE_1
        frame[2] = seq
        frame[3] = cmd
        frame[4] = type
        frame[5] = (payload.size and 0xFF).toByte()
        frame[6] = ((payload.size shr 8) and 0xFF).toByte()
        payload.copyInto(frame, 7)
        return frame
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
