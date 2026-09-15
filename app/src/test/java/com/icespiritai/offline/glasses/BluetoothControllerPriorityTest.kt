package com.icespiritai.offline.glasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [shouldReboostHighOnFirstFa12], the OEM
 * `maybeRetryAiPhotoHighOnFirstChunk` predicate.
 *
 * Units are 1.25 ms: 16 = 20 ms is the OEM's "fast enough" ceiling
 * (`PHOTO_BLE_FAST_INTERVAL_MAX`), 32 = 40 ms is the BALANCED default the
 * glasses link sits at when Android ignores the HIGH request, 6–12 is the
 * 7.5–15 ms band spec §4.1 wants.
 */
class BluetoothControllerPriorityTest {

    @Test
    fun stillBalancedDefault_getsOneRepush() {
        assertTrue(shouldReboostHighOnFirstFa12(32))
    }

    @Test
    fun atTheBoundaryOfTheSlowBand_getsRepush() {
        assertTrue(shouldReboostHighOnFirstFa12(17))
    }

    @Test
    fun alreadyInFastBand_doesNotBurnAnotherRequest() {
        assertFalse(shouldReboostHighOnFirstFa12(16))
        assertFalse(shouldReboostHighOnFirstFa12(12))
        assertFalse(shouldReboostHighOnFirstFa12(6))
    }

    @Test
    fun intervalNeverObserved_doesNotGuess() {
        // onConnectionUpdated is a hidden framework callback; if the ROM
        // never dispatches it the reading stays at the sentinel, and a
        // blind HIGH re-push is exactly the rate-limit risk spec §3.3.2
        // warns about.
        assertFalse(shouldReboostHighOnFirstFa12(0))
        assertFalse(shouldReboostHighOnFirstFa12(-1))
    }
}
