package com.icespiritai.offline.glasses

import android.util.Log

/**
 * FA12 reassembly + stall repair, with no Android or GATT dependencies.
 *
 * This is the part of a capture that decides whether the glasses' JPEG
 * actually arrives. It is kept separate from
 * [GlassesPhotoCaptureRepository.runStages] — which owns the Context, the
 * temp file and the UI state — so it can be driven against a simulated
 * firmware in a plain JVM test (see `GlassesFa12CollectorTest`). The
 * transfer logic it implements was previously untestable and got the
 * block delivery wrong in three distinct ways at once
 * ([runCapturePipeline] has the post-mortem).
 *
 * Wire contract (spec §2.3 Step 4-7): the glasses answer `0x33` with a
 * `0x51 START` carrying `file_size`, then push `offset u32 LE | JPEG
 * bytes` blocks on FA12 until the file is out. The App confirms with a
 * FA11 `0x03 | crc32`; on silence it asks for the first missing byte with
 * FA11 `0x02 | offset`.
 */
internal sealed class Fa12Collection {

    /**
     * Every byte in `[0, file_size)` was covered by a real block.
     * [stream] may be a larger stream than the caller passed in, if the
     * firmware sent past its own declared size.
     */
    data class Complete(
        val stream: GlassesPhotoStream,
        val blocks: Int,
        val resendRounds: Int,
    ) : Fa12Collection()

    /**
     * `0x51 SUCCESS` arrived while gaps were still open — the firmware
     * considers the transfer done (or has stopped caring). Unfilled bytes
     * are padded with `0xFF` so the JPEG header/trailer stay parseable for
     * the OCR frontends that reject raw zero holes; [missingBytesBeforePadding]
     * records how much of the picture is fiction.
     */
    data class CompletedWithGaps(
        val stream: GlassesPhotoStream,
        val blocks: Int,
        val missingBytesBeforePadding: Int,
    ) : Fa12Collection()

    /**
     * Give up. [reason] is user-facing, in the same voice as the rest of
     * the capture state machine.
     */
    data class Failed(val reason: String) : Fa12Collection()
}

/**
 * Parked FFF0 status frames plus their [ReceiveTap].
 *
 * The capture stages pop from one shared queue: stage 2 must be able to
 * hand a frame it does not recognise (the `0x33` Response, which lands
 * while it is still waiting for `0x51 START`) down to stage 3 rather than
 * swallow it — stage 3 is the one that cares about SUCCESS / FAILED.
 */
internal class StatusFrames(private val tap: ReceiveTap<ByteArray>) {

    private val parked = ArrayDeque<ByteArray>()

    /** Move everything already buffered into the parked queue. */
    fun drainBuffered() {
        parked.addAll(tap.drainBuffered())
    }

    /** Parked frame first, then the live inbox without waiting, else `null`. */
    fun poll(): ByteArray? = parked.removeFirstOrNull() ?: tap.poll()

    /** Parked frame first, else wait up to [timeoutMs] for a live one. */
    suspend fun receiveWithin(timeoutMs: Long): ByteArray? =
        parked.removeFirstOrNull() ?: tap.receiveWithin(timeoutMs)

    fun park(frame: ByteArray) {
        parked.addLast(frame)
    }

    fun stop() = tap.stop()
}

/**
 * One FA12 repair batch: [requests] FA11 op2 frames written, and whether the
 * stack refused one.
 *
 * A refusal means the transport is gone, so the collector ends the session on
 * the spot instead of asking a dead handle for the rest of the batch — the
 * 2026-09-17 23:21 session wrote 40 requests into a dropped link over 14 s and
 * the user watched 「补传缺块中」 make no progress until the firmware's own
 * `0x51 FAILED` arrived 50 s later.
 */
internal data class RepairBatch(val requests: Int, val linkRejected: Boolean)

/**
 * Resend rounds to tolerate before giving up on a session that has not
 * received a single FA12 block.
 *
 * The official glasses app (`com.deepvision_tek.glass_front` 3.1.00)
 * logs `AI_PHOTO_RETRANS_ABORT offset=… pkts0=…` and fails with
 * 「未收到图片分片数据(FA12)」 rather than spending its whole 24-round
 * budget, because zero blocks ever arriving means the photo channel is
 * dead, not congested — and 24 × 2.5 s ≈ 60 s is far past the point where
 * a retry is the better advice.
 */
internal const val FA12_NO_SIGNAL_ABORT_ROUNDS = 3

/**
 * How many FA11 `0x02` requests one repair cycle may issue.
 *
 * The single-request-per-round shape (one op2, then a 2.5 s wait) comes
 * from the OEM app and assumes the firmware answers a resend with the
 * whole rest of the file. V2.4.5 does not: 2026-09-16 field logs show
 * **1-3 blocks per op2**, so that shape moved the contiguous prefix by
 * 240-720 B per 2.5 s (~0.3 block/s) — far too slow to repair the tens to
 * hundreds of blocks missing from a lossy burst, which is why the session
 * died with 「眼镜未响应补发请求」 while the glasses were in fact answering
 * every request. Batching distinct offsets spends the same round trip on
 * up to [FA12_REPAIR_BATCH] blocks.
 *
 * Sized from the measured cost of a request: each FA11 write is a
 * write-with-response round trip, ~30 ms on nova 6, and V2.4.5 answers
 * roughly one block per request. 20 requests therefore take ~600 ms of
 * link time and pull in ~20 blocks, which is the range the firmware can
 * sustain without the app's own cycle wait (2026-09-16: 8 per 400 ms
 * cycle repaired 49 KB at ~10 blocks/s, i.e. the pipe sat idle between
 * batches).
 */
internal const val FA12_REPAIR_BATCH = 40

/**
 * Spacing between the offsets requested inside one cycle.
 *
 * Requests walk the missing ranges with this stride, so one cycle asks for
 * blocks the previous request may already have covered. Requests at 30 ms
 * each still cost far less than the 2.5 s the old shape spent per single
 * block, and any offset that turns out to be filled already is dropped by
 * [GlassesPhotoStream.addChunk] as a duplicate.
 *
 * 480 B (2 blocks) is the midpoint of the 1-3 blocks per request V2.4.5
 * was measured answering, so consecutive requests in one cycle should never
 * ask for the same block twice.
 */
internal const val FA12_REPAIR_STRIDE_BYTES = 480

/**
 * Give up on a session that has blocks but has stopped filling gaps for
 * this long.
 *
 * Replaces the OEM's "24 rounds × 2.5 s ≈ 60 s" wait, which measured the
 * wrong thing: with one request per round it counted requests, not the
 * firmware's willingness to answer. Ten seconds of no forward progress is
 * already ~30 unanswered batches, well past the ~80 ms the glasses need
 * per reply, and it keeps the user's retry prompt inside the ~10 s the
 * zero-block path already takes.
 */
internal const val FA12_REPAIR_NO_PROGRESS_MS = 10_000L

/**
 * Drain FA12 blocks from [fa12] (and `0x51` frames from [status]) until
 * [stream] is covered, the firmware ends it, or a budget runs out.
 *
 * Deliberately tolerant of the firmware's messiness, because V2.4.5 is
 * messy: blocks may already be sitting in [fa12] before the START that
 * declares `file_size` (they are, every time — the tap has been open since
 * before `0x33` went out), they may arrive out of order, and the tail
 * block is short (`file_size % 240`), which is why block size is never
 * assumed constant.
 *
 * @param writeFa11 sends a FA11 control packet; returns false when the
 *   stack rejected it. The first op2 of a session waits out a full
 *   [chunkStallMs] of silence (spec §2.3 Step 5 forbids writing while
 *   blocks are still flowing, because FA11 writes starve FA12 notifies);
 *   once repairing, cycles use [resendWaitMs], which is sized to the
 *   ~20-80 ms the firmware actually takes to serve an op2 rather than to
 *   the OEM's 2.5 s round timeout.
 * @param onProgress reports contiguous bytes for the overlay's progress,
 *   plus whether the session is currently repainting gaps (so the overlay
 *   can say so instead of looking stuck on a half-filled progress line).
 * @param onFirstBlock fires once, on the first block of the session, so the
 *   caller can re-push HIGH if the link parameter update never landed
 *   (spec §3.3.3, OEM `maybeRetryAiPhotoHighOnFirstChunk`).
 * @param resendBatchSize FA11 op2 requests issued per repair cycle — see
 *   [FA12_REPAIR_BATCH] for why this is a batch and not a single request.
 * @param resendStrideBytes spacing between the offsets requested in one
 *   cycle — see [FA12_REPAIR_STRIDE_BYTES].
 * @param repairNoProgressMs give up when blocks have arrived but gaps stop
 *   closing for this long — see [FA12_REPAIR_NO_PROGRESS_MS].
 * @param maxRepairCycles cap on repair cycles per session — a request-spam
 *   guard, not the primary budget: [repairNoProgressMs] is what ends a
 *   session whose firmware has stopped answering.
 */
internal suspend fun collectFa12Chunks(
    stream: GlassesPhotoStream,
    fa12: ReceiveTap<ByteArray>,
    status: StatusFrames,
    writeFa11: suspend (ByteArray) -> Boolean,
    onProgress: (contiguousBytes: Int, totalBytes: Int, repairing: Boolean) -> Unit,
    onFirstBlock: () -> Unit = {},
    chunkStallMs: Long,
    resendWaitMs: Long,
    maxRepairCycles: Int,
    timeoutMs: Long,
    resendBatchSize: Int = FA12_REPAIR_BATCH,
    resendStrideBytes: Int = FA12_REPAIR_STRIDE_BYTES,
    repairNoProgressMs: Long = FA12_REPAIR_NO_PROGRESS_MS,
    clock: () -> Long = System::currentTimeMillis,
): Fa12Collection {
    val tag = "GlassesCapture"
    val collectStart = clock()
    var working = stream
    var blocks = 0
    var resendRounds = 0
    /**
     * When the last real block landed. Drives [repairNoProgressMs]: a
     * session that has received blocks but stops filling gaps is finished,
     * no matter how many requests we still have in budget.
     */
    var lastProgressAt = collectStart
    /**
     * True once this session has started asking for missing blocks. Purely
     * for the overlay's wording — the collector's own decisions never read
     * it, since [lastProgressAt] is the honest signal there.
     */
    var repairing = false
    var paddedWithGaps = false
    var paddedBytes = 0
    var terminal: GlassesPhotoProtocol.StatusNotify? = null

    /**
     * Abandon the session and say why.
     *
     * Writes FA11 `0x04` first: the official app does the same on every
     * App-side abort (spec §2.4), because without it the glasses keep
     * streaming a photo nobody is collecting and hold their own capture
     * state — which then shows up as a stall on the *next* attempt.
     * Not used when the firmware itself ended the session (`0x51 FAILED`).
     */
    suspend fun abandon(reason: String): Fa12Collection {
        Log.w(
            tag,
            "abandoning transfer: $reason (blocks=$blocks " +
                "filled=${working.contiguousFilledBytes}/${working.totalSize} resends=$resendRounds)",
        )
        try {
            writeFa11(GlassesPhotoProtocol.buildFa11Cancel())
        } catch (_: Throwable) {
            // The link is usually what broke; the reason above stands.
        }
        return Fa12Collection.Failed(reason)
    }

    // Blocks are applied from two places (the buffered drain and a fresh
    // receive), so the copy lives here.
    fun applyBlock(payload: ByteArray) {
        val chunk = GlassesPhotoProtocol.parsePhotoChunk(payload)
        if (chunk == null) {
            Log.w(tag, "parsePhotoChunk returned null for size=${payload.size}")
            return
        }
        if (chunk.offset + chunk.data.size > working.totalSize) {
            // Firmware sent past its own declared file_size. Grow rather
            // than replace: the old path allocated a fresh stream and lost
            // every byte received so far — which, now that blocks are
            // buffered ahead of START, means losing the head of the JPEG.
            Log.w(tag, "resizing stream: old=${working.totalSize} new=${chunk.offset + chunk.data.size}")
            working = working.grownTo(chunk.offset + chunk.data.size)
        }
        val result = working.addChunk(chunk)
        if (blocks == 0) {
            // Exactly once per session, which is what lets the caller skip
            // the OEM's `aiPhotoPriorityFa12RetryUsed` latch field entirely.
            onFirstBlock()
        }
        blocks++
        // Any accepted block moved the picture forward. Duplicates and
        // out-of-order re-deliveries (`AddResult.Duplicate` /
        // `OutOfOrder`) are the ones that must not refresh the no-progress
        // budget, or a firmware stuck resending one block we already have
        // would look productive forever.
        when (result) {
            is GlassesPhotoStream.AddResult.Added,
            is GlassesPhotoStream.AddResult.Complete,
            -> lastProgressAt = clock()
            else -> Unit
        }
        if (blocks <= 3 || blocks % 20 == 0) {
            Log.d(
                tag,
                "chunk #$blocks offset=${chunk.offset} size=${chunk.data.size} " +
                    "filled=${working.contiguousFilledBytes}/${working.totalSize} " +
                    "highest=${working.highestWrittenOffset}",
            )
        }
        onProgress(working.contiguousFilledBytes, working.totalSize, repairing)
    }

    /**
     * Ask the firmware to retransmit up to [count] distinct missing blocks,
     * starting at the lowest gap at or after [from].
     *
     * One FA11 op2 moves the picture by 1-3 blocks on V2.4.5, so a cycle that
     * asks once leaves the repair at ~0.3 block/s. Walking the gaps with
     * [stride] spends the same wall-clock on up to [count] blocks and keeps
     * the firmware's answers flowing while the next request is in flight.
     *
     * Deliberately no wait between requests: replies are notifications, so
     * they land in [fa12]'s unbounded inbox whether or not this loop is
     * parked, and the caller's cycle wait picks them up. Requesting an
     * offset that a previous reply has already covered is harmless — the
     * stream reports the repeat as a duplicate.
     */
    suspend fun requestMissingBlocks(from: Int, count: Int, stride: Int): RepairBatch {
        var cursor = from
        var requested = 0
        while (requested < count && !working.isComplete) {
            val gap = working.nextMissingRangeFrom(cursor) ?: break
            if (!writeFa11(GlassesPhotoProtocol.buildFa11Resend(gap.first))) {
                // A rejected write means the *link* is gone, not that the
                // glasses are slow. Spamming the rest of the batch buys
                // nothing: the 2026-09-17 23:21 session pushed 40 requests
                // into a dead handle over 14 s, and the user watched
                // 「补传缺块中」 with no progress until the firmware's own
                // 0x51 FAILED arrived 50 s later.
                Log.w(
                    tag,
                    "FA11 op2 write rejected by the stack (request #${requested + 1}) — link is down",
                )
                return RepairBatch(requests = requested + 1, linkRejected = true)
            }
            requested++
            cursor = gap.first + stride
        }
        return RepairBatch(requests = requested, linkRejected = false)
    }

    while (true) {
        if (working.isComplete) {
            Log.d(
                tag,
                "collectChunks complete: ${working.totalSize}/${working.totalSize} bytes " +
                    "covered by $blocks blocks in ${clock() - collectStart}ms (resends=$resendRounds)",
            )
            return if (paddedWithGaps) {
                Fa12Collection.CompletedWithGaps(working, blocks, paddedBytes)
            } else {
                Fa12Collection.Complete(working, blocks, resendRounds)
            }
        }
        if (clock() - collectStart > timeoutMs) {
            Log.w(
                tag,
                "collectChunks hard timeout after $blocks blocks, " +
                    "filled=${working.contiguousFilledBytes}/${working.totalSize} resends=$resendRounds",
            )
            return abandon("传输超时")
        }

        // Spend whatever has already arrived *before* honouring a terminal
        // 0x51: padding the buffer with 0xFF while real blocks were still
        // waiting unread would corrupt a JPEG we could have assembled.
        var appliedFromBuffer = 0
        while (true) {
            val buffered = fa12.poll() ?: break
            applyBlock(buffered)
            appliedFromBuffer++
        }
        if (appliedFromBuffer > 0) continue

        // Fold every 0x51 already in hand before blocking on FA12. The
        // firmware's FAILED arrives precisely because blocks stopped, so
        // waiting a full chunkStallMs to notice it would add dead air to
        // every failed session.
        while (true) {
            val frame = status.poll() ?: break
            when (val parsed = GlassesPhotoProtocol.parseStatusNotify(frame)) {
                is GlassesPhotoProtocol.StatusNotify.Success -> terminal = parsed
                is GlassesPhotoProtocol.StatusNotify.Failed -> terminal = parsed
                else -> Unit
            }
        }
        when (terminal) {
            is GlassesPhotoProtocol.StatusNotify.Failed -> {
                Log.w(
                    tag,
                    "0x51 FAILED during transfer after $blocks blocks, " +
                        "filled=${working.contiguousFilledBytes}/${working.totalSize}",
                )
                return Fa12Collection.Failed("眼镜报告传图失败")
            }
            is GlassesPhotoProtocol.StatusNotify.Success -> {
                val missing = working.missingBytes
                Log.d(
                    tag,
                    "0x51 SUCCESS — force-complete with ${working.contiguousFilledBytes}/" +
                        "${working.totalSize} bytes filled ($blocks blocks, $missing bytes padded)",
                )
                // Pad so OCR sees a well-formed JPEG header. Without
                // padding the assembled buffer is a mosaic of received
                // chunks with literal zero bytes where a 240-byte block is
                // missing; some OCR frontends reject that as malformed.
                working.fillGapsWith(0xFF.toByte())
                paddedWithGaps = true
                paddedBytes += missing
                continue
            }
            else -> Unit
        }

        // Two different silences, two different windows:
        //   - before the first repair, the firmware may just be pausing
        //     inside its burst (2026-09-16 logs show 0.7-2.1 s lulls
        //     between sub-bursts), and spec §2.3 Step 5 forbids writing
        //     while blocks are still flowing, so use the 停包判定 window;
        //   - while repairing, the glasses answer a FA11 op2 in ~20-80 ms
        //     (same logs: op2 write at 19:40:40.186, request served at
        //     .263), so waiting the OEM's 2.5 s per round only throttled
        //     the repair to ~0.3 block/s.
        val waitMs = if (resendRounds > 0) resendWaitMs else chunkStallMs
        val payload = fa12.receiveWithin(waitMs)
        if (payload != null) {
            applyBlock(payload)
            continue
        }

        // Silent for one full window: ask the glasses to retransmit.
        val missing = working.firstMissingRange()
        if (missing == null) continue
        if (blocks == 0 && resendRounds >= FA12_NO_SIGNAL_ABORT_ROUNDS) {
            // Nothing has ever arrived on this channel — say so instead of
            // spending the whole resend budget (official AI_PHOTO_RETRANS_ABORT).
            return abandon("未收到图片分片数据(FA12)，请确认眼镜已连接后重试")
        }
        if (blocks > 0 && clock() - lastProgressAt > repairNoProgressMs) {
            // Blocks did arrive — the channel works — but the gaps have
            // stopped closing. Saying "the glasses did not answer the
            // resend request" here was wrong (they answer every one) and
            // cost the user up to 60 s before the retry prompt.
            val missingBytes = working.totalSize - working.contiguousFilledBytes
            Log.w(
                tag,
                "no FA12 progress for ${clock() - lastProgressAt}ms over $resendRounds repair " +
                    "cycles — giving up (filled=${working.contiguousFilledBytes}/" +
                    "${working.totalSize}, missing=$missingBytes)",
            )
            return abandon("传图未完成（缺 $missingBytes/${working.totalSize} 字节）")
        }
        if (resendRounds >= maxRepairCycles) {
            Log.w(
                tag,
                "no FA12 after $resendRounds op2 batched resends — giving up " +
                    "(filled=${working.contiguousFilledBytes}/${working.totalSize})",
            )
            return abandon("眼镜未响应补发请求")
        }
        resendRounds++
        repairing = true
        // Tell the UI before the round trip, not after: the point of the
        // flag is that the wait the user is about to have is explainable.
        onProgress(working.contiguousFilledBytes, working.totalSize, repairing)
        Log.w(
            tag,
            "FA12 silent — FA11 op2 batch #$resendRounds from ${missing.first} " +
                "(filled=${working.contiguousFilledBytes}/${working.totalSize} " +
                "missing=${missing.first}..${missing.last})",
        )
        val batch = requestMissingBlocks(missing.first, resendBatchSize, resendStrideBytes)
        Log.d(
            tag,
            "FA11 op2 batch #$resendRounds: ${batch.requests} request(s) for " +
                "${working.totalSize - working.contiguousFilledBytes} missing byte(s)",
        )
        if (batch.linkRejected) {
            return abandon("蓝牙连接已断开,请重试")
        }
    }
}
