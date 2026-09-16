package com.icespiritai.offline.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [resolveGlassesTarget] — the decision table that replaced
 * the four-branch chain inside `HomeScreen.launchGlassesCapture`
 * (v0.4.3, 2026-09-16 crash report).
 *
 * Pure JVM: no Bluetooth stack, no Context, no Robolectric. That is the
 * point of the extraction — the branch that crashed the app
 * (no pairing remembered, OS list read on Android 12+) is now reachable in
 * a test that runs in milliseconds.
 */
class GlassesTargetResolverTest {

    private val remembered = GlassesDevice(
        address = "AA:BB:CC:DD:EE:FF",
        name = "Glasses-A88",
        lastSeenMs = 1L,
    )
    private val otherGlasses = GlassesDevice(
        address = "11:22:33:44:55:66",
        name = "Glass-D15 V2.4.5",
        lastSeenMs = 2L,
    )

    // ── Hardware / permission / radio problems win over everything ──────

    @Test
    fun noAdapter_isReportedEvenWhenDevicesAreListed() {
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address,
            snapshot = BondedSnapshot.NO_ADAPTER.copy(bondedGlasses = listOf(otherGlasses)),
            nowMs = NOW,
        )
        assertSame(GlassesTarget.BluetoothUnavailable, target)
    }

    @Test
    fun missingConnectPermission_isReportedBeforeTouchingTheList() {
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address,
            snapshot = BondedSnapshot.PERMISSION_MISSING,
            nowMs = NOW,
        )
        assertSame(GlassesTarget.ConnectPermissionMissing, target)
    }

    @Test
    fun bluetoothOff_isReportedBeforePairing() {
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address,
            snapshot = BondedSnapshot.BLUETOOTH_OFF,
            nowMs = NOW,
        )
        assertSame(GlassesTarget.BluetoothOff, target)
    }

    // ── The crash-report scenario ───────────────────────────────────────

    @Test
    fun nothingRememberedAndNothingBonded_isNotPaired() {
        // The exact configuration the report came from: switch ON, no
        // glasses paired anywhere. Used to throw SecurityException out of
        // the click handler; must now resolve to a prompt-able value.
        val target = resolveGlassesTarget(
            lastPairedAddress = null,
            snapshot = BondedSnapshot(),
            nowMs = NOW,
        )
        assertSame(GlassesTarget.NotPaired, target)
    }

    @Test
    fun nothingRememberedAndOnlyUnrelatedBondedDevices_isNotPaired() {
        // `bondedGlasses` is already prefix-filtered upstream, so an empty
        // list here means "the phone has no *glasses* bonded" even if the
        // OS list is non-empty.
        val target = resolveGlassesTarget(
            lastPairedAddress = null,
            snapshot = BondedSnapshot(bondedGlasses = emptyList()),
            nowMs = NOW,
        )
        assertSame(GlassesTarget.NotPaired, target)
    }

    @Test
    fun blankRememberedAddress_isTreatedAsAbsent() {
        val target = resolveGlassesTarget(
            lastPairedAddress = "   ",
            snapshot = BondedSnapshot(),
            nowMs = NOW,
        )
        assertSame(GlassesTarget.NotPaired, target)
    }

    // ── Happy paths ─────────────────────────────────────────────────────

    @Test
    fun rememberedAddressStillBonded_isPreferred() {
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address,
            snapshot = BondedSnapshot(bondedGlasses = listOf(otherGlasses, remembered)),
            nowMs = NOW,
        ) as GlassesTarget.Ready

        assertEquals(remembered.address, target.device.address)
        assertEquals(GlassesTarget.Ready.Source.SystemBonded, target.source)
        // The OS object (with its real name) is used, not a rebuilt stub.
        assertEquals("Glasses-A88", target.device.name)
    }

    @Test
    fun rememberedAddressMatch_isCaseInsensitive() {
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address.lowercase(),
            snapshot = BondedSnapshot(bondedGlasses = listOf(remembered)),
            nowMs = NOW,
        ) as GlassesTarget.Ready
        assertEquals(remembered.address, target.device.address)
    }

    @Test
    fun bondedDeviceWinsWhenRememberedOneIsGone() {
        // User swapped glasses: the OS list is what can actually
        // authenticate, so it beats the app's stale memory.
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address,
            snapshot = BondedSnapshot(bondedGlasses = listOf(otherGlasses)),
            nowMs = NOW,
        ) as GlassesTarget.Ready

        assertEquals(otherGlasses.address, target.device.address)
        assertEquals(GlassesTarget.Ready.Source.SystemBonded, target.source)
    }

    @Test
    fun bondedDeviceIsUsedWhenNothingIsRemembered() {
        // First launch after a system-settings pairing (the bootstrap path
        // that broke the pre-v0.4.3 chicken-and-egg).
        val target = resolveGlassesTarget(
            lastPairedAddress = null,
            snapshot = BondedSnapshot(bondedGlasses = listOf(remembered)),
            nowMs = NOW,
        ) as GlassesTarget.Ready

        assertEquals(remembered.address, target.device.address)
        assertEquals(GlassesTarget.Ready.Source.SystemBonded, target.source)
    }

    @Test
    fun rememberedAddressIsKeptWhenTheReadableListHasNoMatch() {
        // Pre-v0.4.3 behaviour, preserved deliberately: a peripheral renamed
        // out of our prefix list (or a ROM reporting an empty list) must not
        // turn a working setup into 「未配对」.
        val target = resolveGlassesTarget(
            lastPairedAddress = remembered.address,
            snapshot = BondedSnapshot(bondedGlasses = emptyList()),
            nowMs = NOW,
        ) as GlassesTarget.Ready

        assertEquals(remembered.address, target.device.address)
        assertEquals(GlassesTarget.Ready.Source.RememberedAddress, target.source)
        // Rebuilt from the remembered MAC: the spec-contract name and the
        // injected clock, not a guess at the real broadcast name.
        assertEquals(GlassesDevice.NAME_PREFIX, target.device.name)
        assertEquals(NOW, target.device.lastSeenMs)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
