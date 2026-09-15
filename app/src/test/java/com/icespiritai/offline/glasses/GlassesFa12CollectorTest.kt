package com.icespiritai.offline.glasses

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ── wire-level helpers, shared by the fixture and the tests ────────────
// File-scope rather than instance members because the private Rig class
// below is nested, not inner, and cannot reach the outer test instance.

private const val BLOCK = 240

private fun le32(v: Long) = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v shr 8) and 0xFF).toByte(),
    ((v shr 16) and 0xFF).toByte(),
    ((v shr 24) and 0xFF).toByte(),
)

private fun readLe32(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
        ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

/** `55 AA | seq | cmd | type | len u16 LE | payload`. */
private fun fff0(seq: Int, cmd: Byte, type: Byte, payload: ByteArray): ByteArray {
    val frame = ByteArray(7 + payload.size)
    frame[0] = 0x55
    frame[1] = 0xAA.toByte()
    frame[2] = seq.toByte()
    frame[3] = cmd
    frame[4] = type
    frame[5] = (payload.size and 0xFF).toByte()
    frame[6] = ((payload.size shr 8) and 0xFF).toByte()
    payload.copyInto(frame, 7)
    return frame
}

/** 0x51 START, optionally carrying `file_size u32 LE` (V2.4.5 sends both). */
private fun startFrame(fileSize: Int?): ByteArray = fff0(
    1, 0x51, 0x03,
    if (fileSize == null) byteArrayOf(0x01) else byteArrayOf(0x01) + le32(fileSize.toLong()),
)

private val successFrame = fff0(2, 0x51, 0x03, byteArrayOf(0x02))
private val failedFrame = fff0(3, 0x51, 0x03, byteArrayOf(0x03))

/** The 0x33 capture Response with err=0 — an ack, never a completion. */
private val captureAck = fff0(0, 0x33, 0x02, byteArrayOf(0x00))

/** FA12 notification: `offset u32 LE | JPEG bytes`. */
private fun block(offset: Int, data: ByteArray): ByteArray = le32(offset.toLong()) + data

private fun picture(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

/** Push the blocks covering `[from, to)` of [image], oldest first. */
private fun MutableSharedFlow<ByteArray>.pushRange(image: ByteArray, from: Int, to: Int) {
    var off = from
    while (off < to) {
        val len = minOf(BLOCK, to - off)
        assertTrue("emit offset=$off", tryEmit(block(off, image.copyOfRange(off, off + len))))
        off += len
    }
}

/**
 * The two notify flows (shaped exactly like BluetoothController's —
 * `replay = 0` plus an extra emission buffer), the taps that read them,
 * and a FA11 sink that records the App's control packets and can replay
 * blocks like a firmware that honours op2.
 *
 * The taps are opened in the constructor, on [scope]: that mirrors the
 * real order — `runCapturePipeline` subscribes *before* `0x33` goes out,
 * which is exactly why blocks pushed early are not lost. A fixture that
 * subscribed only when the collector started would recreate the bug it is
 * meant to detect.
 */
private class Rig(scope: kotlinx.coroutines.CoroutineScope) {
    val fa12: MutableSharedFlow<ByteArray> = MutableSharedFlow(extraBufferCapacity = 512)
    val status: MutableSharedFlow<ByteArray> = MutableSharedFlow(extraBufferCapacity = 64)

    val fa12Tap: ReceiveTap<ByteArray> = scope.tapSharedFlow(fa12)
    val statusFrames: StatusFrames = StatusFrames(scope.tapSharedFlow(status))

    val fa11Writes = ArrayList<ByteArray>()

    /** Invoked with the requested offset when the App sends FA11 op2. */
    var onOp2: (Int) -> Unit = {}

    /** Counts first-block callbacks so the caller's once-per-session hook can be asserted. */
    var firstBlocks = 0
    val onFirstBlock: () -> Unit = { firstBlocks++ }

    fun writeFa11(payload: ByteArray): Boolean {
        fa11Writes.add(payload)
        if (payload[0] == GlassesPhotoProtocol.FA11_OP_RESEND) onOp2(readLe32(payload, 1))
        return true
    }

    val op2Offsets: List<Int>
        get() = fa11Writes.filter { it[0] == GlassesPhotoProtocol.FA11_OP_RESEND }
            .map { readLe32(it, 1) }
}

/**
 * Drives [collectFa12Chunks] against a simulated Glass-D15 / Glasses-A88
 * V2.4.5 firmware — the whole "does the picture actually arrive" decision,
 * with no radio and no Context.
 *
 * The scenarios transcribe what the 2026-09-15 field logs showed the
 * firmware doing, because that is what the collector has to survive:
 *
 *   - it pushes FA12 blocks **before** the `0x51 START` that carries
 *     `file_size` (it emits a warm-up START without the trailer, the real
 *     one ~1.8 s later, and blocks are already flowing meanwhile);
 *   - at HIGH priority it drains all 83 blocks of a 19 907 B JPEG in
 *     **0.78 s**, faster than a consumer can be rescheduled;
 *   - it answers a FA11 `0x02` with only the few blocks it still has;
 *   - it gives up with `0x51 FAILED` roughly 8 s after we stop confirming.
 *
 * Waits run on `runTest`'s virtual time, so the production budgets
 * (3.5 s stall / 2.5 s per resend round / 24 rounds / 90 s hard timeout)
 * are exercised without the suite taking minutes.
 */
class GlassesFa12CollectorTest {

    private companion object {
        const val STALL = 3_500L
        const val RESEND_WAIT = 2_500L
        const val MAX_ROUNDS = 24
        const val TIMEOUT = 90_000L
    }

    /** Run the collector over [rig]'s flows, as the repository calls it. */
    private suspend fun TestScope.runCollector(
        rig: Rig,
        totalSize: Int,
        chunkStallMs: Long = STALL,
        maxResendRounds: Int = MAX_ROUNDS,
        timeoutMs: Long = TIMEOUT,
        clock: () -> Long = System::currentTimeMillis,
    ): Fa12Collection = collectFa12Chunks(
        stream = GlassesPhotoStream(totalSize),
        fa12 = rig.fa12Tap,
        status = rig.statusFrames,
        writeFa11 = rig::writeFa11,
        onProgress = { _, _ -> },
        onFirstBlock = rig.onFirstBlock,
        chunkStallMs = chunkStallMs,
        resendWaitMs = RESEND_WAIT,
        maxResendRounds = maxResendRounds,
        timeoutMs = timeoutMs,
        clock = clock,
    )

    @Test
    fun blocksThatArriveBeforeStartAreStillAssembled() = runTest {
        // The V2.4.5 signature. Dropping these leaves a permanent head
        // hole, which is what made every transfer fail before 2026-09-15.
        val image = picture(600)
        val rig = Rig(backgroundScope)
        rig.fa12.pushRange(image, 0, 600)
        assertTrue(rig.status.tryEmit(startFrame(600)))

        val outcome = runCollector(rig, totalSize = 600)

        assertTrue("expected Complete, got $outcome", outcome is Fa12Collection.Complete)
        outcome as Fa12Collection.Complete
        assertEquals(3, outcome.blocks)
        assertEquals(0, outcome.resendRounds)
        assertArrayEquals(image, outcome.stream.assemble())
    }

    @Test
    fun wholePhotoPushedInOneBurstIsNotLossy() = runTest {
        // 83 blocks in 0.78 s: every one of them lands while the consumer
        // is busy with the previous one.
        val image = picture(19_907)
        val rig = Rig(backgroundScope)
        rig.fa12.pushRange(image, 0, image.size)
        assertTrue(rig.status.tryEmit(startFrame(image.size)))

        val outcome = runCollector(rig, totalSize = image.size)

        outcome as Fa12Collection.Complete
        // 82 blocks of 240 B plus a 227 B tail.
        assertEquals(83, outcome.blocks)
        assertEquals(0, outcome.resendRounds)
        assertArrayEquals(image, outcome.stream.assemble())
    }

    @Test
    fun blocksArrivingWhileTheCollectorIsSuspendedAreNotLost() = runTest {
        // The real timing: the collector is parked waiting, then the rest
        // of the file is pushed.
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)

        // UNDISPATCHED so the collector reaches its first suspension now.
        val running = async(start = CoroutineStart.UNDISPATCHED) { runCollector(rig, totalSize = 1_000) }
        rig.fa12.pushRange(image, BLOCK, 1_000)
        advanceUntilIdle()

        val outcome = running.await()
        outcome as Fa12Collection.Complete
        assertEquals(5, outcome.blocks)
        assertArrayEquals(image, outcome.stream.assemble())
    }

    @Test
    fun stallAsksForTheFirstMissingOffsetAndCompletesOnceRepaired() = runTest {
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        rig.onOp2 = { from -> rig.fa12.pushRange(image, from, from + BLOCK) }
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)
        rig.fa12.pushRange(image, 480, 1_000)   // 240..479 never sent

        val outcome = runCollector(rig, totalSize = 1_000)

        outcome as Fa12Collection.Complete
        assertEquals(listOf(240), rig.op2Offsets)
        assertEquals(1, outcome.resendRounds)
        assertArrayEquals(image, outcome.stream.assemble())
    }

    @Test
    fun resendBudgetExhaustionFailsInsteadOfBurningTheWholeTimeout() = runTest {
        // A firmware that never answers op2 must end quickly and truthfully.
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)      // the rest never arrives

        val outcome = runCollector(rig, totalSize = 1_000, maxResendRounds = 3)

        assertTrue("expected Failed, got $outcome", outcome is Fa12Collection.Failed)
        assertEquals("眼镜未响应补发请求", (outcome as Fa12Collection.Failed).reason)
        assertEquals(3, rig.op2Offsets.size)
    }

    @Test
    fun captureAckIsNotTreatedAsTransferCompletion() = runTest {
        // The ack arrives before a single block exists. Treating it as
        // SUCCESS handed OCR a 0xFF-padded stub and reported "拍照成功".
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(captureAck))
        assertTrue(rig.status.tryEmit(startFrame(600)))
        assertTrue(rig.fa12.tryEmit(block(0, ByteArray(BLOCK) { 1 })))

        val outcome = runCollector(rig, totalSize = 600, maxResendRounds = 1)

        assertTrue("the ack must not complete a transfer, got $outcome", outcome is Fa12Collection.Failed)
    }

    @Test
    fun firmwareFailureEndsTheTransferWithoutWaitingOutTheStallWindow() = runTest {
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)
        assertTrue(rig.status.tryEmit(failedFrame))

        val outcome = runCollector(rig, totalSize = 1_000)

        outcome as Fa12Collection.Failed
        assertEquals("眼镜报告传图失败", outcome.reason)
        assertEquals("FAILED must be seen without burning a stall window", 0, rig.fa11Writes.size)
    }

    @Test
    fun successWithGapsPadsAndReportsHowMuchIsFiction() = runTest {
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)
        assertTrue(rig.status.tryEmit(successFrame))

        val outcome = runCollector(rig, totalSize = 1_000)

        outcome as Fa12Collection.CompletedWithGaps
        assertEquals(1_000 - BLOCK, outcome.missingBytesBeforePadding)
        val bytes = outcome.stream.assemble()
        assertEquals(1_000, bytes.size)
        assertArrayEquals(image.copyOfRange(0, BLOCK), bytes.copyOfRange(0, BLOCK))
        assertTrue("padded region must be 0xFF", bytes.copyOfRange(BLOCK, 1_000).all { it == 0xFF.toByte() })
    }

    @Test
    fun outOfOrderHolesAreCountedNotGuessedFromThePrefix() = runTest {
        // Two separated holes. A prefix-based figure would claim every byte
        // past offset 240 is missing and overstate the damage by 240 B.
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)
        rig.fa12.pushRange(image, 480, 720)
        assertTrue(rig.status.tryEmit(successFrame))

        val outcome = runCollector(rig, totalSize = 1_000)

        outcome as Fa12Collection.CompletedWithGaps
        assertEquals(520, outcome.missingBytesBeforePadding)
        val bytes = outcome.stream.assemble()
        assertArrayEquals(image.copyOfRange(480, 720), bytes.copyOfRange(480, 720))
        assertTrue(bytes.copyOfRange(240, 480).all { it == 0xFF.toByte() })
        assertTrue(bytes.copyOfRange(720, 1_000).all { it == 0xFF.toByte() })
    }

    @Test
    fun blockPastDeclaredSizeGrowsWithoutLosingTheHead() = runTest {
        // Firmware under-declares file_size now and then. Growing must keep
        // what was assembled: replacing the stream used to drop the head.
        val image = picture(600)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(400)))
        rig.fa12.pushRange(image, 0, 600)

        val outcome = runCollector(rig, totalSize = 400)

        outcome as Fa12Collection.Complete
        assertEquals(600, outcome.stream.totalSize)
        assertArrayEquals(image, outcome.stream.assemble())
    }

    @Test
    fun silencePastTheHardTimeoutFails() = runTest {
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(600)))
        // Every clock read jumps a day, so the budget is spent at once.
        var now = 0L

        val outcome = runCollector(
            rig,
            totalSize = 600,
            timeoutMs = 1_000L,
            clock = { now += 86_400_000L; now },
        )

        assertEquals("传输超时", (outcome as Fa12Collection.Failed).reason)
        assertEquals(
            "an App-side abandon must end with FA11 op4 so the glasses stop pushing",
            listOf(GlassesPhotoProtocol.FA11_OP_CANCEL),
            rig.fa11Writes.map { it[0] },
        )
    }

    @Test
    fun firstBlockHookFiresExactlyOncePerSession() = runTest {
        // The OEM latches this with aiPhotoPriorityFa12RetryUsed; here the
        // collector's own block count is what bounds it, because a hook that
        // fires per block would spam requestConnectionPriority and trip the
        // ROM rate limit spec §3.3.2 warns about.
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, 1_000)

        val outcome = runCollector(rig, totalSize = 1_000)

        outcome as Fa12Collection.Complete
        assertEquals(5, outcome.blocks)
        assertEquals(1, rig.firstBlocks)
    }

    @Test
    fun noBlocksAtAllAbortsEarlyInsteadOfSpendingTheWholeResendBudget() = runTest {
        // Zero FA12 ever arriving means the photo channel is dead, not
        // congested; the official app reports that distinctly
        // (AI_PHOTO_RETRANS_ABORT … pkts0) rather than burning 24 rounds.
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))

        val outcome = runCollector(rig, totalSize = 1_000)

        outcome as Fa12Collection.Failed
        assertTrue(
            "unexpected reason: ${outcome.reason}",
            outcome.reason.contains("未收到图片分片"),
        )
        assertEquals(FA12_NO_SIGNAL_ABORT_ROUNDS, rig.op2Offsets.size)
    }

    @Test
    fun firmwareReportedFailureDoesNotSendACancelPacket() = runTest {
        // The glass already ended the session; op4 would be noise.
        val image = picture(1_000)
        val rig = Rig(backgroundScope)
        assertTrue(rig.status.tryEmit(startFrame(1_000)))
        rig.fa12.pushRange(image, 0, BLOCK)
        assertTrue(rig.status.tryEmit(failedFrame))

        runCollector(rig, totalSize = 1_000)

        assertEquals(emptyList<Byte>(), rig.fa11Writes.map { it[0] })
    }
}
