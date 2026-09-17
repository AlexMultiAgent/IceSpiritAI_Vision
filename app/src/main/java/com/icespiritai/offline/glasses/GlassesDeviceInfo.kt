package com.icespiritai.offline.glasses

/**
 * What the glasses report about themselves — the fields that decide which
 * photo path this hardware can use.
 *
 * Read from the `0x10` device-info command we already use for
 * [firmwareVersion]. The byte layouts below are not guesses: they are the
 * ones the OEM app (`com.deepvision_tek.glass_front` 3.1.00,
 * `BluetoothController.handleReceivedData`) applies to the very same
 * response, cross-checked against a capture from our own glasses.
 *
 * The OEM uses them to pick a transfer strategy. A device that reports
 * storage hands an ordinary photo over FTP-over-AP/P2P (`hasUsableStorage`),
 * while a *memoryless* one — no storage at all — is streamed over
 * SPP/RFCOMM instead (`PhotoCaptureService.waitForSppPhotoViaClassic`,
 * `GFSP`/`GFSA`). Which world our hardware lives in was an open question
 * until this read existed.
 *
 * @param firmwareVersion e.g. `V2.4.6` (`0x10|0x20`, UTF-8).
 * @param usedStorageMb / [totalStorageMb] the two `u64` LE of the memory
 *   answer. The OEM reads `isMemoryless = used <= 0` — a device with no
 *   storage reports nothing used, and one with storage always has some of
 *   it occupied.
 * @param unsyncedFiles files waiting to be pulled (`0x10|0x17`; the `0x11`
 *   notify carries a 1-byte form of the same count).
 * @param ftpIp FTP server to pull photos from once the glasses are in AP
 *   mode. Reported as four bytes in **reverse** order; all-zero means "AP
 *   mode is not running", not "server at 0.0.0.0".
 * @param p2pMac Wi-Fi Direct MAC — the other media-sync rendezvous.
 * @param apSsid / [apPassword] the AP-mode credentials, empty until the
 *   glasses provision an AP session.
 * @param rawHex every answer's raw payload, so a field whose format still
 *   surprises us is visible in the log instead of being silently dropped.
 */
data class GlassesDeviceInfo(
    val firmwareVersion: String?,
    val usedStorageMb: Long?,
    val totalStorageMb: Long?,
    val unsyncedFiles: Int?,
    val ftpIp: String?,
    val p2pMac: String?,
    val apSsid: String?,
    val apPassword: String?,
    val apAccountBytes: Int?,
    val rawHex: Map<String, String> = emptyMap(),
) {

    /** Did the memory answer arrive in full (both halves)? */
    val storageInfoKnown: Boolean
        get() = usedStorageMb != null && totalStorageMb != null

    /**
     * The OEM app's `DeviceInfo.isMemoryless` — "no storage on this
     * hardware", which is what sends it down the SPP/RFCOMM path.
     *
     * `null` when the glasses did not answer the memory read: that is
     * "unknown", not "memoryless", and the two call for different advice.
     */
    val memoryless: Boolean? get() = usedStorageMb?.let { it <= 0L }

    /** The OEM app's `hasUsableStorage` — storage the media sync can pull from. */
    val hasUsableStorage: Boolean get() = storageInfoKnown && (totalStorageMb ?: 0L) > 0

    /**
     * Does this device advertise the Wi-Fi media-sync path at all? True even
     * while AP mode is idle (the P2P MAC is the rendezvous the OEM starts
     * with `0x39 p2pStart`).
     */
    val offersWifiTransfer: Boolean
        get() = p2pMac != null || ftpIp != null || !apSsid.isNullOrBlank()

    /**
     * Did the glasses actually enter a transfer session — an address or
     * credentials handed out, not just the P2P MAC they always report?
     *
     * This is the difference between "the hardware supports Wi-Fi transfer"
     * and "the Wi-Fi session is up and something could be pulled from it
     * right now", which is what a `0x36` + `0x39` request is supposed to
     * produce.
     */
    val apSessionUp: Boolean get() = ftpIp != null || !apSsid.isNullOrBlank()

    /**
     * One `0x10` device-info field: [label] is the key used in [rawHex] and
     * in the repository's `Map<String, ByteArray>`, [subCmd] the sub-command
     * the glasses expect in the request.
     */
    enum class Field(
        val label: String,
        val subCmd: Byte,
        /**
         * May this field also be read out of a `0x11` status notify?
         *
         * Only the firmware version is: the OEM's notify parser publishes
         * battery (0x01), video (0x0B), audio (0x0C), photo result (0x17) and
         * PAN (0x15) — nothing else. It matters for [FILES] in particular,
         * because `0x17` in a notify is the *shutter event*, and treating it
         * as the file count would read "0 files pending" from the very
         * notification that says a photo was just taken.
         */
        val fromStatusNotify: Boolean = false,
    ) {
        FIRMWARE("firmware", GlassesPhotoProtocol.SUB_FIRMWARE_INFO, fromStatusNotify = true),
        MEMORY("memory", GlassesPhotoProtocol.SUB_MEMORY),
        FILES("files", GlassesPhotoProtocol.SUB_FILE_COUNT),
        FTP("ftp", GlassesPhotoProtocol.SUB_FTP_IP),
        P2P("p2p", GlassesPhotoProtocol.SUB_P2P_MAC),
        AP_ACCOUNT("apAccount", GlassesPhotoProtocol.SUB_AP_ACCOUNT),
    }

    companion object {

        /**
         * Decode whatever subset of [answers] (keyed by [Field.label]) the
         * glasses replied with. Missing fields stay `null`.
         *
         * Shapes follow the OEM app exactly (see the class docs). Anything
         * that does not match a known shape stays `null` and remains visible
         * in [rawHex] instead of being mangled into a number.
         */
        fun fromAnswers(answers: Map<String, ByteArray>): GlassesDeviceInfo {
            val memory = answers[Field.MEMORY.label]?.takeIf { it.size >= MEMORY_SIZE }
            val ap = answers[Field.AP_ACCOUNT.label]?.let(::apAccount)

            return GlassesDeviceInfo(
                firmwareVersion = answers[Field.FIRMWARE.label]?.let(::firmwareString),
                usedStorageMb = memory?.let { littleEndianLong(it, 0, 8) },
                totalStorageMb = memory?.let { littleEndianLong(it, 8, 8) },
                unsyncedFiles = answers[Field.FILES.label]?.let(::fileCount),
                ftpIp = answers[Field.FTP.label]?.let(::ftpAddress),
                p2pMac = answers[Field.P2P.label]?.let(::macAddress),
                apSsid = ap?.first,
                apPassword = ap?.second,
                apAccountBytes = answers[Field.AP_ACCOUNT.label]?.size,
                rawHex = answers.mapValues { (_, value) -> value.toHex() },
            )
        }

        /**
         * The memory answer is two `u64` LE — used then total — so a short
         * one is not a number we can trust.
         */
        private const val MEMORY_SIZE = 16

        /** `[u16 ×2]` marker the file-count struct carries before the count. */
        private const val FILE_COUNT_STRUCT_SIZE = 6

        /** Raw Wi-Fi Direct MAC length; anything longer is the printable form. */
        private const val MAC_SIZE = 6

        /** Little-endian unsigned integer of 1, 2, 4 or 8 bytes, else `null`. */
        internal fun littleEndianLong(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long? {
            if (length != 1 && length != 2 && length != 4 && length != 8) return null
            if (offset < 0 || offset + length > bytes.size) return null
            var value = 0L
            for (i in (offset + length - 1) downTo offset) {
                value = (value shl 8) or (bytes[i].toLong() and 0xFF)
            }
            return value
        }

        /**
         * The unsynced count, in either shape the firmware uses:
         * `[4 bytes][u32 LE count]` from the `0x10` read, or a single byte
         * from a `0x11` notify.
         */
        internal fun fileCount(bytes: ByteArray): Int? = when {
            bytes.size >= FILE_COUNT_STRUCT_SIZE -> littleEndianLong(bytes, 2, 4)?.toInt()
            bytes.size == 1 -> bytes[0].toInt() and 0xFF
            else -> null
        }

        /** ASCII/UTF-8 text with NUL padding and control bytes rejected. */
        internal fun printableText(bytes: ByteArray): String? = bytes
            .toString(Charsets.UTF_8)
            .trim { it <= ' ' || it == '\u0000' }
            .takeIf { it.isNotBlank() && it.all { c -> c.code in 0x20..0x7E } }

        /**
         * The FTP address as a dotted quad of **reversed** bytes.
         *
         * `0.0.0.0` is what the glasses report while AP mode is not running
         * (`requestFtpIp` clears the cached value before asking), so it maps
         * to `null` — an unusable address is not "an address".
         */
        internal fun ftpAddress(bytes: ByteArray): String? = when {
            bytes.size == 4 -> bytes
                .reversedArray()
                .takeIf { quad -> quad.any { it.toInt() != 0 } }
                ?.joinToString(".") { (it.toInt() and 0xFF).toString() }
            // Some paths answer with the printable address instead.
            else -> printableText(bytes)
        }

        /**
         * A MAC address as `AA:BB:CC:DD:EE:FF`, from either the raw 6 bytes
         * or the printable form the firmware may send instead.
         */
        internal fun macAddress(bytes: ByteArray): String? = when {
            bytes.size == MAC_SIZE -> bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
            else -> printableText(bytes)
        }

        /**
         * The AP credential blob: a `[marker][len][utf8]…` list where marker
         * `1` is the SSID and `2` the password. Empty entries (which is what
         * the glasses send when no AP session is provisioned) stay `null`.
         */
        internal fun apAccount(bytes: ByteArray): Pair<String?, String?> {
            var ssid: String? = null
            var password: String? = null
            var offset = 0
            while (offset + 2 <= bytes.size) {
                val marker = bytes[offset].toInt() and 0xFF
                val length = bytes[offset + 1].toInt() and 0xFF
                if (offset + 2 + length > bytes.size) break
                val value = printableText(bytes.copyOfRange(offset + 2, offset + 2 + length))
                when (marker) {
                    1 -> ssid = value
                    2 -> password = value
                }
                offset += 2 + length
            }
            return ssid to password
        }

        /** UTF-8 version string, NUL-padded inside its own length is common. */
        internal fun firmwareString(bytes: ByteArray): String? = bytes
            .toString(Charsets.UTF_8)
            .takeWhile { it != '\u0000' }
            .trim()
            .takeIf { it.isNotBlank() }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
