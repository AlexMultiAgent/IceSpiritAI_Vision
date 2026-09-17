package com.icespiritai.offline.glasses

import com.icespiritai.offline.glasses.BluetoothController.ConnectionState
import com.icespiritai.offline.glasses.GlassesPhotoCaptureRepository.GlassesCaptureDevice
import com.icespiritai.offline.glasses.GlassesPhotoCaptureRepository.GlassesCaptureState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `Ready`-but-dead-link rule behind the 2026-09-17 bug report:
 * after the glasses' OTA reboot the settings row said
 * 「固件版本读取超时」 and only an App restart fixed it, because
 * `ensureConnected` trusted its cached `Ready` state and returned before
 * reconnecting.
 */
class GlassesLinkLivenessTest {

    private val device = GlassesDevice(
        address = "C4:12:22:55:60:0B",
        name = "Glasses-A88",
        lastSeenMs = 0L,
    )

    private val captureDevice = GlassesCaptureDevice.from(device)

    @Test
    fun readyPlusLiveConnectionIsUsable() {
        assertTrue(
            isGlassesLinkUsable(
                GlassesCaptureState.Ready(captureDevice),
                ConnectionState.Connected(device, mtu = 517),
                device.address,
            ),
        )
    }

    @Test
    fun readyWithDroppedLinkIsNotUsable() {
        // The reboot case: nothing demoted the repository state.
        assertFalse(
            isGlassesLinkUsable(
                GlassesCaptureState.Ready(captureDevice),
                ConnectionState.Disconnected,
                device.address,
            ),
        )
        assertFalse(
            isGlassesLinkUsable(
                GlassesCaptureState.Ready(captureDevice),
                ConnectionState.Idle,
                device.address,
            ),
        )
        assertFalse(
            isGlassesLinkUsable(
                GlassesCaptureState.Ready(captureDevice),
                ConnectionState.Failed("status=8"),
                device.address,
            ),
        )
    }

    @Test
    fun readyForAnotherDeviceIsNotUsable() {
        val other = GlassesDevice(
            address = "C4:12:22:55:60:09",
            name = "Glasses-A88",
            lastSeenMs = 0L,
        )
        assertFalse(
            isGlassesLinkUsable(
                GlassesCaptureState.Ready(captureDevice),
                ConnectionState.Connected(other, mtu = 517),
                device.address,
            ),
        )
    }

    @Test
    fun addressComparisonIgnoresCase() {
        // Addresses come from two sources (our store vs. the GATT callback)
        // and Android does not promise a case for them.
        assertTrue(
            isGlassesLinkUsable(
                GlassesCaptureState.Ready(captureDevice),
                ConnectionState.Connected(
                    device.copy(address = device.address.lowercase()),
                    mtu = 517,
                ),
                device.address,
            ),
        )
    }

    @Test
    fun nonReadyRepositoryStatesAreNeverUsable() {
        val states = listOf(
            GlassesCaptureState.Idle,
            GlassesCaptureState.Connecting(captureDevice),
            GlassesCaptureState.Failed("连接超时", retryable = true),
        )
        for (state in states) {
            assertFalse(
                "expected $state to be unusable",
                isGlassesLinkUsable(state, ConnectionState.Connected(device, 517), device.address),
            )
        }
    }
}
