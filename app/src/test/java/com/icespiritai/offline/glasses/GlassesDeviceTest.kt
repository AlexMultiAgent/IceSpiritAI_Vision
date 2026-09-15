package com.icespiritai.offline.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [GlassesDevice] data class.
 *
 * The class is a pure value holder — no Android dependencies, no Bluetooth
 * stack interaction. [GlassesScan] filtering and [GlassesDeviceStore]
 * persistence are exercised by androidTest on a real device.
 */
class GlassesDeviceTest {

    @Test
    fun equality_allFieldsMatch() {
        // Standard data class equality — `lastSeenMs` IS part of the
        // identity. Two sightings at different times are two different
        // snapshots, even if they describe the same physical glasses.
        val now = System.currentTimeMillis()
        val a = GlassesDevice(address = "AA:BB:CC:DD:EE:FF", name = "Glass-D15 V2.4.5", lastSeenMs = now)
        val b = GlassesDevice(address = "AA:BB:CC:DD:EE:FF", name = "Glass-D15 V2.4.5", lastSeenMs = now)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun inequality_differentLastSeenMs() {
        // Snapshot semantics: a fresher sighting is a different value.
        val now = System.currentTimeMillis()
        val a = GlassesDevice(address = "AA:BB:CC:DD:EE:FF", name = "Glass-D15 V2.4.5", lastSeenMs = now)
        val b = GlassesDevice(address = "AA:BB:CC:DD:EE:FF", name = "Glass-D15 V2.4.5", lastSeenMs = now + 1000)
        assertNotEquals(a, b)
    }

    @Test
    fun inequality_distinctAddresses() {
        val a = GlassesDevice("AA:BB:CC:DD:EE:01", "Glass-D15 V2.4.5", 0)
        val b = GlassesDevice("AA:BB:CC:DD:EE:02", "Glass-D15 V2.4.5", 0)
        assertNotEquals(a, b)
    }

    @Test
    fun mapDedupe_byAddress_overwritesLastSeenMs() {
        // The scanner uses `LinkedHashMap<address, GlassesDevice>` keyed
        // by MAC to dedupe repeated sightings — this is the production
        // dedup mechanism (NOT structural equality). Verify the contract.
        val map = LinkedHashMap<String, GlassesDevice>()
        val t1 = 1_000L
        val t2 = 2_000L
        map["AA:BB:CC:DD:EE:FF"] = GlassesDevice("AA:BB:CC:DD:EE:FF", "Glass-D15 V2.4.5", t1)
        map["AA:BB:CC:DD:EE:FF"] = GlassesDevice("AA:BB:CC:DD:EE:FF", "Glass-D15 V2.4.5", t2)
        assertEquals(1, map.size)
        assertEquals(t2, map.values.first().lastSeenMs)
    }

    @Test
    fun namePrefix_isStable() {
        // Pin the spec-contract prefix so a silent refactor is caught.
        assertEquals("Glass-D15", GlassesDevice.NAME_PREFIX)
    }

    @Test
    fun namePrefixes_containsBothShippedVariants() {
        // Pin the dual-prefix contract — adding a new OEM / firmware
        // revision should be a deliberate, test-visible change rather
        // than a silent expansion.
        assertTrue(
            "Glass-D15 must remain an accepted prefix (spec contract)",
            "Glass-D15" in GlassesDevice.NAME_PREFIXES,
        )
        assertTrue(
            "Glasses-A must remain an accepted prefix (field hardware)",
            "Glasses-A" in GlassesDevice.NAME_PREFIXES,
        )
    }

    @Test
    fun nameFilter_acceptsBothShippedVariants() {
        // Spec hardware (`Glass-D15 V2.4.5`) AND field hardware
        // (`Glasses-A88`) must both pass the scan / bonded-device
        // filter. First observed 2026-09-14 (commit `552a8a7`).
        assertTrue(GlassesDevice.nameMatches("Glass-D15 V2.4.5"))
        assertTrue(GlassesDevice.nameMatches("Glasses-A88"))
    }

    @Test
    fun nameFilter_rejectsUnrelatedDevice() {
        val candidates = listOf(
            "Mi Smart Band 7",
            "Galaxy Watch5",
            "AirPods Pro",
            "Glass-D14",          // older model — different prefix
            "GLASS-D15",          // case-sensitive: firmware advertises with mixed case
            "Glasses-B200",       // near-miss for `Glasses-A` but a different OEM
        )
        candidates.forEach {
            assertFalse(
                "$it should not match",
                GlassesDevice.nameMatches(it),
            )
        }
    }

    @Test
    fun nameFilter_rejectsNullAndBlank() {
        assertFalse(GlassesDevice.nameMatches(null))
        assertFalse(GlassesDevice.nameMatches(""))
        assertFalse(GlassesDevice.nameMatches("   "))
    }

    @Test
    fun staleThreshold_isAtLeast30Seconds() {
        // Pin a lower bound so the value doesn't drift below what's
        // mentioned in CLAUDE.md / plan.
        assertTrue(
            "stale threshold too aggressive: ${GlassesDevice.STALE_THRESHOLD_MS}",
            GlassesDevice.STALE_THRESHOLD_MS >= 30_000L,
        )
    }
}
