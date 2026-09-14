package com.icespiritai.offline.glasses

import java.util.zip.CRC32

/**
 * Assembles FA12 JPEG chunks into a complete JPEG byte stream and tracks
 * which offsets are still missing so the caller can issue FA11 op2 resend
 * commands.
 *
 * **State model.** The class holds a single `[totalSize]`-byte buffer plus
 * a parallel `BooleanArray` marking which bytes have been filled. After
 * every [addChunk] it advances a `contiguousBytes` cursor as far as the
 * contiguous prefix of filled bytes reaches. `contiguousBytes == totalSize`
 * means the JPEG is complete. The [firstMissingRange] helper is the
 * "where do I tell the glasses to retransmit from" answer — the caller
 * uses that offset to build an FA11 op2 packet via
 * [GlassesPhotoProtocol.buildFa11Resend].
 *
 * **Why a `BooleanArray` instead of a `SortedSet<Int>` of received
 * offsets?** Range-based queries ("is byte 12345 filled?") are O(1)
 * here vs O(log n) per query in a sorted set. With an 18 KB JPEG the
 * difference is marginal, but the bitmap also lets us find missing
 * ranges in a single linear scan in [firstMissingRange] without
 * allocating any intermediate collections.
 *
 * **No Android imports.** Pure JVM data + `CRC32` — directly unit-
 * testable without Robolectric.
 */
class GlassesPhotoStream(val totalSize: Int) {

    init {
        require(totalSize > 0) { "totalSize must be positive, was $totalSize" }
    }

    private val buffer: ByteArray = ByteArray(totalSize)
    private val received: BooleanArray = BooleanArray(totalSize)
    private val seenStartOffsets: HashSet<Int> = HashSet()

    /**
     * Bytes `[0, contiguousBytes)` are filled (no gaps). Maintained
     * incrementally so each [addChunk] is O(chunk size) in the worst case,
     * O(1) amortized across a sequential in-order stream.
     */
    private var contiguousBytes: Int = 0

    /** Highest byte offset the caller has successfully written, or `-1` if no chunks yet. */
    private var highestWrittenOffset: Int = -1

    /**
     * Result of [addChunk].
     *
     * - [Added] — chunk accepted, stream not yet complete. [Added.filledBytes]
     *   is the current contiguous-prefix length (i.e. how much of the JPEG
     *   can be safely handed to the OCR engine without losing data).
     * - [Complete] — chunk filled the last gap. [Complete.totalBytes]
     *   equals [totalSize].
     * - [OutOfOrder] — chunk's offset is below the contiguous prefix
     *   (caller should treat as no-op; the byte range is already filled).
     * - [Duplicate] — chunk's start offset was already seen in a prior call.
     *   Belt-and-braces alongside [OutOfOrder] for callers that want to
     *   surface duplicate deliveries in logs without re-copying bytes.
     * - [OutOfRange] — chunk extends past [totalSize]. Almost always
     *   indicates a corrupt glasses-side file_size or a bytes-misaligned
     *   FA12 offset; bail out.
     */
    sealed class AddResult {
        data class Added(val filledBytes: Int, val highestWrittenOffset: Int) : AddResult()
        data class Complete(val totalBytes: Int) : AddResult()
        data class OutOfOrder(val offset: Int, val expectedNextFilledByte: Int) : AddResult()
        data class Duplicate(val offset: Int) : AddResult()
        data class OutOfRange(val offset: Int, val length: Int, val totalSize: Int) : AddResult()
    }

    /**
     * Insert one FA12 chunk into the assembly buffer.
     *
     * Chunks may arrive out of order or with gaps; the stream tolerates
     * any delivery order as long as the **set** of covered byte ranges
     * eventually equals `[0, totalSize)`. The caller drives the
     * "request retransmit for first missing range" loop using
     * [firstMissingRange].
     */
    fun addChunk(chunk: GlassesPhotoProtocol.PhotoChunk): AddResult {
        val end = chunk.offset + chunk.data.size
        if (end > totalSize) {
            return AddResult.OutOfRange(chunk.offset, chunk.data.size, totalSize)
        }
        if (chunk.offset < contiguousBytes) {
            // Some or all of this chunk's bytes are already covered. We
            // still want to copy any tail that lands past contiguousBytes
            // (firmware occasionally sends overlapping chunks during a
            // resend storm). Walk the boundary rather than blind-copy.
            if (end > contiguousBytes) {
                val copyFrom = contiguousBytes
                val copyLen = end - copyFrom
                System.arraycopy(chunk.data, copyFrom - chunk.offset, buffer, copyFrom, copyLen)
                advanceContiguousCursor()
                return if (contiguousBytes >= totalSize) {
                    AddResult.Complete(totalSize)
                } else {
                    AddResult.Added(contiguousBytes, highestWrittenOffset)
                }
            }
            // Entirely below contiguousBytes — duplicate or stale.
            return if (chunk.offset in seenStartOffsets) {
                AddResult.Duplicate(chunk.offset)
            } else {
                AddResult.OutOfOrder(chunk.offset, contiguousBytes)
            }
        }
        if (chunk.offset in seenStartOffsets) {
            // Strictly above contiguousBytes but same start as a prior
            // chunk — the prior copy already happened. No-op.
            return AddResult.Duplicate(chunk.offset)
        }
        System.arraycopy(chunk.data, 0, buffer, chunk.offset, chunk.data.size)
        for (i in chunk.offset until end) {
            received[i] = true
        }
        seenStartOffsets.add(chunk.offset)
        if (chunk.data.size > 0) {
            highestWrittenOffset = maxOf(highestWrittenOffset, end - 1)
        }
        advanceContiguousCursor()
        return if (contiguousBytes >= totalSize) {
            AddResult.Complete(totalSize)
        } else {
            AddResult.Added(contiguousBytes, highestWrittenOffset)
        }
    }

    /**
     * Returns the lowest contiguous missing byte range, or `null` if the
     * stream is complete. The caller uses `start` as the offset argument
     * for [GlassesPhotoProtocol.buildFa11Resend] when the glasses stall.
     *
     * In practice the glasses deliver chunks strictly in order and the
     * range is always `[contiguousBytes, contiguousBytes + N)` for some
     * small N — but the bitmap-based scan handles any permutation.
     */
    fun firstMissingRange(): IntRange? {
        if (contiguousBytes >= totalSize) return null
        var i = contiguousBytes
        while (i < totalSize && received[i]) i++
        if (i >= totalSize) return null
        val start = i
        while (i < totalSize && !received[i]) i++
        return start until i
    }

    /**
     * True iff every byte `[0, totalSize)` has been filled. After this
     * returns true, [assemble] / [crc32] are safe to call.
     */
    val isComplete: Boolean get() = contiguousBytes >= totalSize

    /**
     * Number of bytes in the contiguous prefix `[0, n)`. Used by callers
     * to drive UI progress (e.g. "接收中 12 800 / 18 432 字节").
     */
    val contiguousFilledBytes: Int get() = contiguousBytes

    /**
     * Assemble the JPEG into a fresh `ByteArray`. Throws if [isComplete]
     * is false — callers must drain to completion before invoking the
     * OCR engine (a partial JPEG is unrecoverable for OCR).
     */
    fun assemble(): ByteArray {
        check(isComplete) { "stream not complete: $contiguousBytes / $totalSize bytes filled" }
        return buffer.copyOf()
    }

    /**
     * Compute CRC32 over the assembled JPEG. Throws if [isComplete] is
     * false. Result is `Long` to match `java.util.zip.CRC32.getValue()`
     * semantics; the high bit may be set.
     */
    fun crc32(): Long {
        check(isComplete) { "stream not complete: $contiguousBytes / $totalSize bytes filled" }
        val crc = CRC32()
        crc.update(buffer, 0, totalSize)
        return crc.value
    }

    /**
     * Reset the stream to a clean state. Useful when the caller decides to
     * abort a capture (timeout, user cancel) and immediately start a new
     * one without reallocating.
     */
    fun reset() {
        java.util.Arrays.fill(received, false)
        seenStartOffsets.clear()
        contiguousBytes = 0
        highestWrittenOffset = -1
        // Don't zero `buffer` — overwrite happens on next addChunk, and
        // zeroing a multi-MB buffer on every reset is wasted work.
    }

    private fun advanceContiguousCursor() {
        while (contiguousBytes < totalSize && received[contiguousBytes]) {
            contiguousBytes++
        }
    }
}
