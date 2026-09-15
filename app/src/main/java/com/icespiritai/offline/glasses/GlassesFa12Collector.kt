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
 *   stack rejected it. Only ever called after a full [chunkStallMs] of
 *   silence — spec §2.3 Step 5 forbids writing while blocks are still
 *   flowing, because FA11 writes starve FA12 notifies.
 * @param onProgress reports contiguous bytes for the overlay's progress.
 */
internal suspend fun collectFa12Chunks(
    stream: GlassesPhotoStream,
    fa12: ReceiveTap<ByteArray>,
    status: StatusFrames,
    writeFa11: suspend (ByteArray) -> Boolean,
    onProgress: (contiguousBytes: Int, totalBytes: Int) -> Unit,
    chunkStallMs: Long,
    resendWaitMs: Long,
    maxResendRounds: Int,
    timeoutMs: Long,
    clock: () -> Long = System::currentTimeMillis,
): Fa12Collection {
    val tag = "GlassesCapture"
    val collectStart = clock()
    var working = stream
    var blocks = 0
    var resendRounds = 0
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
        working.addChunk(chunk)
        blocks++
        if (blocks <= 3 || blocks % 20 == 0) {
            Log.d(
                tag,
                "chunk #$blocks offset=${chunk.offset} size=${chunk.data.size} " +
                    "filled=${working.contiguousFilledBytes}/${working.totalSize} " +
                    "highest=${working.highestWrittenOffset}",
            )
        }
        onProgress(working.contiguousFilledBytes, working.totalSize)
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

        // Shorter wait once a resend is outstanding (spec §2.3 Step 6
        // 单轮等待 ~2.5 s vs the 停包判定 ~3.5 s that opens a session).
        val payload = fa12.receiveWithin(if (resendRounds > 0) resendWaitMs else chunkStallMs)
        if (payload != null) {
            applyBlock(payload)
            continue
        }

        // Silent for one full window: ask the glasses to retransmit from
        // the first byte we are still missing (spec §2.3 Step 6).
        val missing = working.firstMissingRange()
        if (missing == null) continue
        if (blocks == 0 && resendRounds >= FA12_NO_SIGNAL_ABORT_ROUNDS) {
            // Nothing has ever arrived on this channel — say so instead of
            // spending the whole resend budget (official AI_PHOTO_RETRANS_ABORT).
            return abandon("未收到图片分片数据(FA12)，请确认眼镜已连接后重试")
        }
        if (resendRounds >= maxResendRounds) {
            Log.w(
                tag,
                "no FA12 after $resendRounds op2 resends — giving up " +
                    "(filled=${working.contiguousFilledBytes}/${working.totalSize})",
            )
            return abandon("眼镜未响应补发请求")
        }
        resendRounds++
        Log.w(
            tag,
            "FA12 silent — FA11 op2 #$resendRounds from ${missing.first} " +
                "(filled=${working.contiguousFilledBytes}/${working.totalSize} " +
                "missing=${missing.first}..${missing.last})",
        )
        if (!writeFa11(GlassesPhotoProtocol.buildFa11Resend(missing.first))) {
            Log.w(tag, "FA11 op2 write rejected by the stack (round $resendRounds)")
        }
    }
}
