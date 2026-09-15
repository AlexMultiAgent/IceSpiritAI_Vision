package com.icespiritai.offline.glasses

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ReceiveTap], the primitive that keeps the FA10 photo
 * transfer lossless.
 *
 * They encode the failure that broke the glasses link: the FA12 notify
 * flow is `replay = 0`, so every chunk the glasses pushed while the
 * pipeline sat between two `flow.first()` calls was discarded with no
 * error surfaced — `tryEmit` returns `true` for a dropped,
 * unsubscribed emission. The lost blocks always included the head of the
 * JPEG, so assembly never reached 100 %, the FA11 CRC confirm was never
 * written, and the firmware aborted with `0x51 FAILED`.
 *
 * Payloads are [Int] here rather than JPEG bytes: the property under
 * test is delivery, not parsing.
 */
class GlassesReceiveTapTest {

    /** Same shape as BluetoothController's FA12 flow. */
    private fun source() = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 512)

    @Test
    fun tap_keepsEveryEmissionThatPrecedesTheFirstRead() = runTest {
        val source = source()
        val tap = backgroundScope.tapSharedFlow(source)

        // The glasses push a whole photo before the collector gets going:
        // 0x51 START carries file_size ~1.8 s after the warm-up START, and
        // blocks are already flowing by then.
        repeat(83) { assertTrue("emit $it", source.tryEmit(it)) }

        val received = (1..83).map { tap.receiveWithin(1_000L) }
        assertEquals((0 until 83).toList(), received)
        tap.stop()
    }

    @Test
    fun tap_keepsBufferingWhileTheConsumerIsBusy() = runTest {
        val source = source()
        val tap = backgroundScope.tapSharedFlow(source)

        assertTrue(source.tryEmit(0))
        assertEquals(0, tap.receiveWithin(1_000L))

        // "Busy" = the collector is parsing / assembling / publishing
        // progress instead of sitting in a receive. Under the old
        // per-read `first()` these four were lost.
        repeat(4) { assertTrue(source.tryEmit(it + 1)) }

        assertEquals(listOf(1, 2, 3, 4), (1..4).map { tap.receiveWithin(1_000L) })
        tap.stop()
    }

    @Test
    fun replayZeroFlowDropsEmissionsThatHaveNoSubscriberYet() = runTest {
        // The counter-proof, and the reason `first()` is the wrong tool
        // here: tryEmit reports success, yet a later subscriber never
        // sees the value. This is the invisible half of the FA12 loss.
        val source = source()
        assertTrue(source.tryEmit(42))

        assertNull(withTimeoutOrNull(10_000L) { source.first() })
    }

    @Test
    fun drainBuffered_returnsOldestFirstAndEmptiesTheInbox() = runTest {
        val source = source()
        val tap = backgroundScope.tapSharedFlow(source)
        repeat(5) { source.tryEmit(it) }

        assertEquals(listOf(0, 1, 2, 3, 4), tap.drainBuffered().toList())
        assertNull(tap.poll())
        tap.stop()
    }

    @Test
    fun poll_returnsNullWithoutWaitingWhenEmpty() = runTest {
        val source = source()
        val tap = backgroundScope.tapSharedFlow(source)

        assertNull(tap.poll())
        assertTrue(source.tryEmit(7))
        assertEquals(7, tap.poll())
        tap.stop()
    }

    @Test
    fun stop_releasesTheSubscription() = runTest {
        val source = source()
        val tap = backgroundScope.tapSharedFlow(source)
        assertEquals(1, source.subscriptionCount.value)

        tap.stop()

        assertEquals(0, source.subscriptionCount.value)
    }

    @Test
    fun tap_startsAcceptingBeforeTheCallerDoesAnythingElse() = runTest {
        // The capture path opens its taps and only then writes 0x33, so a
        // subscription must already be live when `tapSharedFlow` returns.
        val source = source()
        val tap = backgroundScope.tapSharedFlow(source)

        source.tryEmit(1)

        assertEquals(1, tap.poll())
        tap.stop()
    }
}
