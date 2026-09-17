package com.icespiritai.offline.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `0x33` capture-command acknowledgement: `55 AA | seq | 33 | 02 | 01 00 | err`.
 *
 * The busy case is the one that matters for the hardware shutter button: the
 * wearer presses it, the glasses report the photo, we fire 0x33 — and the
 * glasses answer `err=1` because they are still busy with the shot they just
 * took (real frame `55aa003302010001`, 2026-09-17 08:12:53). The OEM retries
 * once after 400 ms; [GlassesPhotoProtocol.captureAckError] is what lets the
 * pipeline tell "rejected, worth one retry" from "not an ack at all".
 */
class GlassesCaptureAckTest {

    @Test
    fun acceptedAckHasErrorZero() {
        val frame = byteArrayOf(0x55, 0xAA.toByte(), 0x00, 0x33, 0x02, 0x01, 0x00, 0x00)
        assertEquals(0, GlassesPhotoProtocol.captureAckError(frame))
        assertTrue(GlassesPhotoProtocol.isCaptureAckSuccess(frame))
    }

    @Test
    fun busyRejectionCarriesErrOne() {
        val frame = byteArrayOf(0x55, 0xAA.toByte(), 0x00, 0x33, 0x02, 0x01, 0x00, 0x01)
        assertEquals(1, GlassesPhotoProtocol.captureAckError(frame))
        assertFalse(GlassesPhotoProtocol.isCaptureAckSuccess(frame))
    }

    @Test
    fun nonAckFramesAreNotMistakenForRejections() {
        // 0x51 START — a different command.
        assertNull(
            GlassesPhotoProtocol.captureAckError(
                byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x51, 0x03, 0x01, 0x00, 0x01),
            ),
        )
        // 0x33 Request (our own echo), not a Response.
        assertNull(
            GlassesPhotoProtocol.captureAckError(
                byteArrayOf(0x55, 0xAA.toByte(), 0x00, 0x33, 0x01, 0x01, 0x00, 0x50),
            ),
        )
        // Right command, wrong payload length.
        assertNull(
            GlassesPhotoProtocol.captureAckError(
                byteArrayOf(0x55, 0xAA.toByte(), 0x00, 0x33, 0x02, 0x02, 0x00, 0x00, 0x01),
            ),
        )
    }
}
