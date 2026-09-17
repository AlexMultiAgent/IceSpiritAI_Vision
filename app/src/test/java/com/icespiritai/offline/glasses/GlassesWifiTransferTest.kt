package com.icespiritai.offline.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Wi-Fi media-transfer probe: `0x36` (Wi-Fi on/off) and `0x39` (P2P / AP
 * mode), which are the precondition for the OEM's FTP photo path.
 *
 * Byte values come from the OEM APK's own config, `assets/app_config.json`
 * (`cmdOpenWifi: 54`, `cmdStartP2p: 57`, `p2pStartPayload: 2`,
 * `p2pStopPayload: 0`, `wifiOnPayload: 1`, `wifiOffPayload: 0`) and from
 * `BluetoothController.openWifi` / `startP2pMode` / `startTransferApMode` /
 * `stopP2pMode`, which all build a one-byte-payload Request frame.
 */
class GlassesWifiTransferTest {

    @Test
    fun switchPayloadsMatchTheVendorConfig() {
        assertEquals(54, GlassesPhotoProtocol.CMD_OPEN_WIFI.toInt())
        assertEquals(57, GlassesPhotoProtocol.CMD_START_P2P.toInt())
        assertEquals(62, GlassesPhotoProtocol.CMD_BLUETOOTH_NETWORK.toInt())
        assertEquals(1, GlassesPhotoProtocol.WIFI_ON_PAYLOAD.toInt())
        assertEquals(0, GlassesPhotoProtocol.WIFI_OFF_PAYLOAD.toInt())
        assertEquals(2, GlassesPhotoProtocol.P2P_START_PAYLOAD.toInt())
        // `startTransferApMode` and `stopP2pMode` both send 0.
        assertEquals(0, GlassesPhotoProtocol.P2P_STOP_PAYLOAD.toInt())
    }

    /**
     * The command that makes the glasses actually *use* the phone's network.
     *
     * OEM `BlePacketBuilder.buildBluetoothNetworkSharingPacket(seq, on)` builds
     * payload `01 01 <on>`, and the OTA flow calls it before handing over the
     * URL (`enqueueOtaPayloadAfterPanReady`). Without it an upgrade can sit in
     * "downloading" forever: the phone's tethering toggle only *offers* PAN.
     */
    @Test
    fun networkSharingCommandMatchesTheVendorPayload() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x01),
            GlassesPhotoProtocol.buildBluetoothNetworkSharingPayload(on = true),
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x00),
            GlassesPhotoProtocol.buildBluetoothNetworkSharingPayload(on = false),
        )
        // `55 AA | 61 | 3E | 01 | 03 00 | 01 01 01`
        assertArrayEquals(
            byteArrayOf(
                0x55, 0xAA.toByte(),
                0x61,
                0x3E,
                0x01,
                0x03, 0x00,
                0x01, 0x01, 0x01,
            ),
            GlassesPhotoProtocol.buildBluetoothNetworkSharingFrame(seq = 0x61, on = true),
        )
    }

    @Test
    fun wifiOnIsAOneByteRequest() {
        // `55 AA | 61 | 36 | 01 | 01 00 | 01`
        assertArrayEquals(
            byteArrayOf(
                0x55, 0xAA.toByte(),
                0x61,
                0x36,           // cmdOpenWifi
                0x01,           // Request
                0x01, 0x00,     // payload length u16 LE = 1
                0x01,           // wifiOnPayload
            ),
            GlassesPhotoProtocol.buildSwitchRequestFrame(
                seq = 0x61,
                cmd = GlassesPhotoProtocol.CMD_OPEN_WIFI,
                value = GlassesPhotoProtocol.WIFI_ON_PAYLOAD,
            ),
        )
        // …and off is the same frame with 0x00.
        assertArrayEquals(
            byteArrayOf(0x55, 0xAA.toByte(), 0x62, 0x36, 0x01, 0x01, 0x00, 0x00),
            GlassesPhotoProtocol.buildSwitchRequestFrame(
                seq = 0x62,
                cmd = GlassesPhotoProtocol.CMD_OPEN_WIFI,
                value = GlassesPhotoProtocol.WIFI_OFF_PAYLOAD,
            ),
        )
    }

    @Test
    fun p2pStartAndStopFrames() {
        // `55 AA | 63 | 39 | 01 | 01 00 | 02`
        assertArrayEquals(
            byteArrayOf(0x55, 0xAA.toByte(), 0x63, 0x39, 0x01, 0x01, 0x00, 0x02),
            GlassesPhotoProtocol.buildSwitchRequestFrame(
                seq = 0x63,
                cmd = GlassesPhotoProtocol.CMD_START_P2P,
                value = GlassesPhotoProtocol.P2P_START_PAYLOAD,
            ),
        )
        // `55 AA | 71 | 39 | 01 | 01 00 | 00`
        assertArrayEquals(
            byteArrayOf(0x55, 0xAA.toByte(), 0x71, 0x39, 0x01, 0x01, 0x00, 0x00),
            GlassesPhotoProtocol.buildSwitchRequestFrame(
                seq = 0x71,
                cmd = GlassesPhotoProtocol.CMD_START_P2P,
                value = GlassesPhotoProtocol.P2P_STOP_PAYLOAD,
            ),
        )
    }

    @Test
    fun onlyAnAddressOrAnAccountMeansTheSessionIsUp() {
        // Idle hardware: the P2P MAC is always reported, and it alone must not
        // be mistaken for a live session.
        val idle = GlassesDeviceInfo.fromAnswers(
            mapOf("p2p" to byteArrayOf(0xC4.toByte(), 0x12, 0x22, 0x55, 0x60, 0x08)),
        )
        assertFalse(idle.apSessionUp)
        assertTrue(idle.offersWifiTransfer)

        // A real capture: FTP 192.168.43.1 arrived (reversed bytes).
        val up = GlassesDeviceInfo.fromAnswers(
            mapOf("ftp" to byteArrayOf(0x01, 0x2B, 0xA8.toByte(), 0xC0.toByte())),
        )
        assertEquals("192.168.43.1", up.ftpIp)
        assertTrue(up.apSessionUp)

        // Credentials without an address is still a session.
        val withAccount = GlassesDeviceInfo.fromAnswers(
            mapOf(
                "apAccount" to byteArrayOf(0x01, 0x06) + "GF-AP1".toByteArray(Charsets.UTF_8),
            ),
        )
        assertEquals("GF-AP1", withAccount.apSsid)
        assertTrue(withAccount.apSessionUp)
    }

    @Test
    fun fileCountIsNotReadFromStatusNotifies() {
        // The trap: `0x17` in a `0x11` notify is the *shutter event*
        // (OEM `mediaPhotoResult`), not the unsynced file count. Both
        // sub-commands really are 23, so only the channel disambiguates.
        val shutter = ByteArray(7 + 3)
        byteArrayOf(0x55, 0xAA.toByte(), 0x15, 0x11, 0x03, 0x03, 0x00, 0x17, 0x01, 0x00)
            .copyInto(shutter)

        assertTrue(GlassesPhotoProtocol.isDeviceStatusNotify(shutter))
        // The raw extractor is channel-agnostic and does return the bytes…
        assertArrayEquals(
            byteArrayOf(0x00),
            GlassesPhotoProtocol.deviceInfoValue(shutter, GlassesPhotoProtocol.SUB_FILE_COUNT),
        )
        // …so the caller is the one that must refuse: reading it as a count
        // would say "0 files pending" while the frame says a photo was taken.
        assertFalse(GlassesDeviceInfo.Field.FILES.fromStatusNotify)
        assertFalse(GlassesDeviceInfo.Field.MEMORY.fromStatusNotify)
        assertFalse(GlassesDeviceInfo.Field.FTP.fromStatusNotify)
        assertFalse(GlassesDeviceInfo.Field.P2P.fromStatusNotify)
        assertFalse(GlassesDeviceInfo.Field.AP_ACCOUNT.fromStatusNotify)
        // Only the firmware version is mirrored into notifies.
        assertTrue(GlassesDeviceInfo.Field.FIRMWARE.fromStatusNotify)
    }

    @Test
    fun deviceInfoResponsesAreNotNotifies() {
        val response = ByteArray(9)
        byteArrayOf(0x55, 0xAA.toByte(), 0x41, 0x10, 0x02, 0x02, 0x00, 0x17, 0x00)
            .copyInto(response)

        assertFalse(GlassesPhotoProtocol.isDeviceStatusNotify(response))
        // Nonsense bytes are not a notify either.
        assertFalse(GlassesPhotoProtocol.isDeviceStatusNotify(byteArrayOf(0x00, 0x00, 0x00)))
    }
}
