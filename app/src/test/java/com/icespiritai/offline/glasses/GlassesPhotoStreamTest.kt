package com.icespiritai.offline.glasses

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

/**
 * Unit tests for [GlassesPhotoStream] chunk assembly.
 *
 * The stream is exercised in-order, out-of-order, with duplicates, with
 * gaps, and across reset cycles. CRC32 output is compared against an
 * independently-computed `java.util.zip.CRC32` reference to verify the
 * assembled bytes are byte-identical to the input.
 */
class GlassesPhotoStreamTest {

    private fun byteRange(start: Int, count: Int): ByteArray =
        ByteArray(count) { i -> ((start + i) and 0xFF).toByte() }

    @Test(expected = IllegalArgumentException::class)
    fun construction_zeroSize_throws() {
        GlassesPhotoStream(totalSize = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun construction_negativeSize_throws() {
        GlassesPhotoStream(totalSize = -1)
    }

    @Test
    fun inOrderChunks_completesStream() {
        val total = 1000
        val stream = GlassesPhotoStream(total)
        val expected = byteRange(0, total)

        for (offset in 0 until total step 100) {
            val len = minOf(100, total - offset)
            val data = byteRange(offset, len)
            val r = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(offset, data))
            assertTrue(
                "offset=$offset: expected Added or Complete, got $r",
                r is GlassesPhotoStream.AddResult.Added || r is GlassesPhotoStream.AddResult.Complete,
            )
        }
        assertTrue(stream.isComplete)
        assertArrayEquals(expected, stream.assemble())
    }

    @Test
    fun singleChunk_fullSize_completesImmediately() {
        val data = byteRange(0, 240)
        val stream = GlassesPhotoStream(240)
        val r = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, data))
        assertTrue(r is GlassesPhotoStream.AddResult.Complete)
        assertEquals(240, (r as GlassesPhotoStream.AddResult.Complete).totalBytes)
        assertTrue(stream.isComplete)
        assertArrayEquals(data, stream.assemble())
    }

    @Test
    fun outOfOrderChunks_completesWhenAllRangesCovered() {
        // Three chunks: [0..239], [240..479], [480..719] of a 720-byte JPEG.
        // Deliver in reverse order so each arrival extends the assembly
        // but never shortens the still-missing prefix until the last chunk
        // lands.
        val total = 720
        val stream = GlassesPhotoStream(total)

        // 1. Highest-range chunk arrives first — bytes [480..719] are
        //    written, but [0..479] are still missing entirely. The
        //    contiguous prefix is therefore still 0.
        val c2 = byteRange(480, 240)
        val r2 = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(480, c2))
        assertTrue(r2 is GlassesPhotoStream.AddResult.Added)
        assertEquals(0, (r2 as GlassesPhotoStream.AddResult.Added).filledBytes)

        // 2. Middle chunk [240..479] arrives — still missing [0..239].
        val c1 = byteRange(240, 240)
        val r1 = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(240, c1))
        assertTrue(r1 is GlassesPhotoStream.AddResult.Added)
        assertEquals(0, (r1 as GlassesPhotoStream.AddResult.Added).filledBytes)

        // 3. Lowest chunk [0..239] arrives — fills the last gap and the
        //    contiguous prefix jumps all the way to totalSize.
        val c0 = byteRange(0, 240)
        val r0 = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, c0))
        assertTrue(r0 is GlassesPhotoStream.AddResult.Complete)
        assertEquals(720, (r0 as GlassesPhotoStream.AddResult.Complete).totalBytes)

        val expected = ByteArray(total) { i -> (i and 0xFF).toByte() }
        assertArrayEquals(expected, stream.assemble())
    }

    @Test
    fun duplicateChunk_isDetected() {
        val stream = GlassesPhotoStream(500)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 100)))
        val dup = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 100)))
        assertTrue("expected Duplicate, got $dup", dup is GlassesPhotoStream.AddResult.Duplicate)
    }

    @Test
    fun outOfRangeChunk_isRejected() {
        val stream = GlassesPhotoStream(100)
        val r = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(50, byteRange(0, 200)))
        assertTrue(r is GlassesPhotoStream.AddResult.OutOfRange)
        assertEquals(50, (r as GlassesPhotoStream.AddResult.OutOfRange).offset)
        assertEquals(200, r.length)
        assertEquals(100, r.totalSize)
        assertFalse(stream.isComplete)
    }

    @Test
    fun firstMissingRange_reportsLowestGap() {
        val stream = GlassesPhotoStream(1000)
        // Fill 0..499 via two chunks, leave 500..999 missing, fill 800..999 by mistake.
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 250)))
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(250, byteRange(250, 250)))
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(800, byteRange(800, 200)))

        val range = stream.firstMissingRange()
        assertNotNull(range)
        assertEquals(500, range!!.first)
        assertEquals(800, range.last + 1)  // IntRange end-exclusive
    }

    @Test
    fun firstMissingRange_completedStream_returnsNull() {
        val stream = GlassesPhotoStream(240)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 240)))
        assertNull(stream.firstMissingRange())
    }

    @Test
    fun crc32_matchesReference() {
        val total = 1024
        val stream = GlassesPhotoStream(total)
        val bytes = byteRange(0, total)

        // Split into random-ish chunks for realism.
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, bytes.copyOfRange(0, 300)))
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(300, bytes.copyOfRange(300, 700)))
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(700, bytes.copyOfRange(700, total)))
        assertTrue(stream.isComplete)

        val expectedCrc = CRC32().also { it.update(bytes, 0, total) }.value
        assertEquals(expectedCrc, stream.crc32())
    }

    @Test(expected = IllegalStateException::class)
    fun assemble_beforeComplete_throws() {
        val stream = GlassesPhotoStream(100)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 50)))
        stream.assemble()
    }

    @Test(expected = IllegalStateException::class)
    fun crc32_beforeComplete_throws() {
        val stream = GlassesPhotoStream(100)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 50)))
        stream.crc32()
    }

    @Test
    fun contiguousFilledBytes_tracksProgress() {
        val stream = GlassesPhotoStream(500)
        assertEquals(0, stream.contiguousFilledBytes)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 100)))
        assertEquals(100, stream.contiguousFilledBytes)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(200, byteRange(200, 100)))
        // Out of order, doesn't extend the contiguous prefix
        assertEquals(100, stream.contiguousFilledBytes)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(100, byteRange(100, 100)))
        assertEquals(300, stream.contiguousFilledBytes)
    }

    @Test
    fun reset_allowsReuse() {
        val stream = GlassesPhotoStream(100)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 100)))
        assertTrue(stream.isComplete)
        stream.reset()
        assertFalse(stream.isComplete)
        assertEquals(0, stream.contiguousFilledBytes)
        // New fill cycle works.
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 100)))
        assertTrue(stream.isComplete)
    }

    @Test
    fun overlappingChunk_belowContiguousPrefix_copiesTailOnly() {
        // Simulate a partial overlap from a resend storm: the firmware
        // re-sends bytes 0..239, but contiguous prefix has already moved to
        // 250 (chunk 100..249 was filled first). The new chunk's tail
        // [250..239+240)... no wait, that's not how overlapping works.
        // Use a clearer scenario:
        //   initial fill: chunk(0, 240) → contiguous = 240
        //   firmware re-sends chunk(0, 240) → overlap, no progress, classified as Duplicate
        //   then firmware re-sends chunk(100, 200) → bytes 100..299 are an
        //   exact re-do of [100..239] + a tail [240..299]. Since [100..239]
        //   are already filled, only [240..299] should be copied.
        val stream = GlassesPhotoStream(300)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 240)))
        assertEquals(240, stream.contiguousFilledBytes)
        // Out-of-order chunk overlapping the existing prefix — should be
        // classified Duplicate (start offset already seen), no copy.
        val overlap = stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 240)))
        assertTrue("expected Duplicate, got $overlap", overlap is GlassesPhotoStream.AddResult.Duplicate)
        assertEquals(240, stream.contiguousFilledBytes)
    }

    @Test
    fun assemble_returnsIndependentCopy() {
        val stream = GlassesPhotoStream(50)
        stream.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 50)))
        val a = stream.assemble()
        val b = stream.assemble()
        // Different array references but same content — caller can hold on
        // to either without aliasing the stream's internal buffer.
        assertFalse(a === b)
        assertArrayEquals(a, b)
    }

    // ── grownTo ────────────────────────────────────────────────────────
    // The firmware occasionally sends past the file_size it declared in
    // 0x51 START. Growing used to mean `GlassesPhotoStream(newSize)`,
    // which silently dropped every byte already assembled — fatal now
    // that blocks are buffered ahead of START (they always include the
    // head of the JPEG).

    @Test
    fun grownTo_preservesAlreadyReceivedBytes() {
        val original = GlassesPhotoStream(240)
        original.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 240)))
        assertTrue(original.isComplete)

        val grown = original.grownTo(480)
        assertFalse("growing must not leave the stream complete", grown.isComplete)
        assertEquals(240, grown.contiguousFilledBytes)
        // Coverage bits were copied too: the only gap left is the new
        // tail, not anything inside the old window.
        val gap = grown.firstMissingRange()
        assertNotNull(gap)
        assertEquals(240, gap!!.first)
    }

    @Test
    fun grownTo_completesAfterReceivingTheTail() {
        val original = GlassesPhotoStream(240)
        original.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 240)))
        val grown = original.grownTo(480)
        grown.addChunk(GlassesPhotoProtocol.PhotoChunk(240, byteRange(240, 240)))

        assertTrue(grown.isComplete)
        assertArrayEquals(byteRange(0, 480), grown.assemble())
        assertEquals(
            CRC32().also { it.update(byteRange(0, 480)) }.value,
            grown.crc32(),
        )
    }

    @Test
    fun grownTo_reportsGapBeyondTheOldCeiling() {
        // A hole that starts inside the old window and runs past it must
        // still be reported, or the FA11 op2 resend would ask for the
        // wrong offset.
        val grown = GlassesPhotoStream(100)
            .also { it.addChunk(GlassesPhotoProtocol.PhotoChunk(0, byteRange(0, 50))) }
            .grownTo(300)
        grown.addChunk(GlassesPhotoProtocol.PhotoChunk(100, byteRange(100, 200)))

        val range = grown.firstMissingRange()
        assertNotNull(range)
        assertEquals(50, range!!.first)
        assertEquals(100, range.last + 1)
    }

    @Test
    fun grownTo_preservesHeadHoleWhenPrefixWasIncomplete() {
        // Head-of-file missing (the pre-START-loss signature): contiguous
        // prefix stays 0 across the grow, and the gap still reports from 0.
        val grown = GlassesPhotoStream(240)
            .also { it.addChunk(GlassesPhotoProtocol.PhotoChunk(120, byteRange(120, 120))) }
            .grownTo(480)
        assertEquals(0, grown.contiguousFilledBytes)
        assertEquals(0, grown.firstMissingRange()!!.first)
    }

    @Test(expected = IllegalArgumentException::class)
    fun grownTo_smallerOrEqualSize_throws() {
        GlassesPhotoStream(240).grownTo(240)
    }
}
