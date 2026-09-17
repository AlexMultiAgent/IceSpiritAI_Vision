package com.icespiritai.offline.glasses

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The upgrade dialog's precondition warning.
 *
 * Background: the glasses fetch the `.rbl` themselves, and their only route
 * to the internet is the phone's 「蓝牙共享网络」. On 2026-09-17 that was off,
 * the App had nothing to say about it, and the user watched a 20-minute
 * "downloading" timer that could never finish (version stuck at V2.4.6 for
 * 798 s, no `bt-pan` interface on the phone).
 *
 * The rule is deliberately asymmetric: only the glasses' own report (`0x15`
 * TLV = 0) produces the definite warning, and only after a grace period;
 * silence produces the vague one. So a firmware that never sends the TLV — or
 * a polarity mistake — can never turn into a confident false claim.
 */
class GlassesFirmwareTetheringHintTest {

    @Test
    fun notSharingWarnsOnceTheGracePeriodHasPassed() {
        assertEquals(
            GlassesFirmwareUpdater.TetheringHint.NONE,
            hint(sharing = false, elapsedMs = 0),
        )
        assertEquals(
            GlassesFirmwareUpdater.TetheringHint.NONE,
            hint(sharing = false, elapsedMs = 59_000),
        )
        assertEquals(
            GlassesFirmwareUpdater.TetheringHint.TETHERING_OFF,
            hint(sharing = false, elapsedMs = 60_000),
        )
        assertEquals(
            GlassesFirmwareUpdater.TetheringHint.TETHERING_OFF,
            hint(sharing = false, elapsedMs = 798_000),
        )
    }

    @Test
    fun aWorkingShareIsNeverAProblem() {
        for (elapsed in listOf(0L, 60_000L, 200_000L, 798_000L)) {
            assertEquals(
                GlassesFirmwareUpdater.TetheringHint.NONE,
                hint(sharing = true, elapsedMs = elapsed),
            )
        }
    }

    @Test
    fun silenceOnlyWarnsAfterThreeMinutes() {
        assertEquals(
            GlassesFirmwareUpdater.TetheringHint.NONE,
            hint(sharing = null, elapsedMs = 179_000),
        )
        assertEquals(
            GlassesFirmwareUpdater.TetheringHint.NO_PROGRESS,
            hint(sharing = null, elapsedMs = 180_000),
        )
    }

    private fun hint(sharing: Boolean?, elapsedMs: Long) =
        firmwareTetheringHint(sharing, elapsedMs)
}
