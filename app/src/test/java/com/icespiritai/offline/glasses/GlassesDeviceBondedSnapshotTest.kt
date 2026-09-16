package com.icespiritai.offline.glasses

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothDevice

/**
 * Robolectric tests for [GlassesDevice.bondedSnapshot] — the function that
 * replaced the unguarded `BluetoothAdapter.bondedDevices` read which
 * crashed the app on Android 12+ (v0.4.3, 2026-09-16 crash report).
 *
 * `sdk = 33` because that is where the crash happened: API 31+ is the world
 * where `BLUETOOTH_CONNECT` is enforced, and the repo's Robolectric setup
 * caps at 34 (targetSdk is 37).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassesDeviceBondedSnapshotTest {

    private val context = RuntimeEnvironment.getApplication()
    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    @Test
    fun deniedConnectPermission_returnsPermissionMissingInsteadOfThrowing() {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        // The regression assertion: pre-v0.4.3 this call path threw
        // SecurityException ("Need android.permission.BLUETOOTH_CONNECT
        // permission ... getBondedDevices") on the main thread, inside a
        // Compose onClick — an un-survivable crash.
        val snapshot = GlassesDevice.bondedSnapshot(context)

        assertFalse(
            "a denied BLUETOOTH_CONNECT must be reported, not thrown",
            snapshot.connectPermissionGranted,
        )
        assertTrue(
            "a permission-denied snapshot must not claim to know any device",
            snapshot.bondedGlasses.isEmpty(),
        )
    }

    @Test
    fun grantedPermission_readsMatchingBondedGlasses() {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(adapter).setEnabled(true)
        shadowOf(adapter).setBondedDevices(
            setOf(
                bondedDevice("AA:BB:CC:DD:EE:FF", "Glasses-A88"),
                bondedDevice("11:22:33:44:55:66", "Galaxy Watch5"),
            ),
        )

        val snapshot = GlassesDevice.bondedSnapshot(context)

        assertTrue(snapshot.connectPermissionGranted)
        assertEquals(1, snapshot.bondedGlasses.size)
        assertEquals("AA:BB:CC:DD:EE:FF", snapshot.bondedGlasses.first().address)
        assertEquals("Glasses-A88", snapshot.bondedGlasses.first().name)
    }

    @Test
    fun grantedPermission_keepsPermissionFlagWhenNoGlassesAreBonded() {
        // "Paired a watch, not the glasses" must stay distinguishable from
        // "we were not allowed to look": the card and the notice dialog say
        // different things for the two.
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(adapter).setEnabled(true)
        shadowOf(adapter).setBondedDevices(
            setOf(bondedDevice("11:22:33:44:55:66", "Mi Smart Band 7")),
        )

        val snapshot = GlassesDevice.bondedSnapshot(context)

        assertTrue(snapshot.connectPermissionGranted)
        assertTrue(snapshot.bondedGlasses.isEmpty())
    }

    @Test
    fun bluetoothOff_isReportedSeparatelyFromNotPaired() {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(adapter).setEnabled(false)

        val snapshot = GlassesDevice.bondedSnapshot(context)

        assertFalse(snapshot.bluetoothEnabled)
        assertTrue(snapshot.bondedGlasses.isEmpty())
    }

    private fun bondedDevice(address: String, name: String): BluetoothDevice =
        ShadowBluetoothDevice.newInstance(address).also { shadowOf(it).setName(name) }
}
