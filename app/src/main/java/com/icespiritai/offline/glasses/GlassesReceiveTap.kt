package com.icespiritai.offline.glasses

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A lossless, long-lived subscription to one of the hot
 * [SharedFlow]s in [BluetoothController].
 *
 * **Why this exists.** Both notify flows are `replay = 0`, so a payload
 * that arrives while nobody is subscribed is thrown away — and
 * `tryEmit` still reports `true`, so the loss leaves no trace. Reading
 * such a flow with `flow.first()` (or anything else that subscribes per
 * read) therefore silently drops every notification produced outside the
 * one call that happens to be suspended at the time. That is exactly how
 * the FA10 photo transfer lost 30-50 % of its JPEG blocks, and since the
 * losses always included the head of the file, the assembly buffer could
 * never report completion and the glasses aborted the session with
 * `0x51 FAILED`. See
 * `docs/glasses/AI识图传图-App端接收处理说明.md` §2.3 Step 4
 * ("START 之前冲进来的 FA12 进 aiPhotoEarlyChunks，START 后回填") — the
 * OEM reference solves the same problem by reassembling synchronously in
 * `onCharacteristicChanged` and parking pre-START blocks.
 *
 * **How it solves it.** [tapSharedFlow] subscribes once and republishes
 * into a [Channel] with `UNLIMITED` capacity: buffered items survive a
 * busy or not-yet-listening consumer, and a timed-out `receive` cannot
 * lose the item that raced it (unlike a cancelled `first()`). The tap is
 * created before the capture command goes out, so it also covers the
 * pre-START window for free.
 *
 * Obtained via [tapSharedFlow]; never construct directly.
 */
internal class ReceiveTap<T> private constructor(
    /** Backing queue. Public so a stage can `select`/`receive` on it directly. */
    val inbox: Channel<T>,
    private val tap: Job,
) {

    /**
     * Everything currently buffered, oldest first, removed from the
     * inbox. Lets one stage hand the frames it does not recognise down to
     * the next stage instead of swallowing them.
     */
    fun drainBuffered(): ArrayDeque<T> {
        val parked = ArrayDeque<T>()
        while (true) {
            val value = inbox.tryReceive().getOrNull() ?: break
            parked.addLast(value)
        }
        return parked
    }

    /** One buffered item without waiting, or `null` if the inbox is empty. */
    fun poll(): T? = inbox.tryReceive().getOrNull()

    /**
     * The next item, waiting up to [timeoutMs]. `null` on timeout — the
     * item is not lost either way, since [inbox] is unbounded.
     */
    suspend fun receiveWithin(timeoutMs: Long): T? =
        withTimeoutOrNull(timeoutMs) { inbox.receive() }

    /** Tear down the subscription. Idempotent. */
    fun stop() {
        tap.cancel()
    }

    companion object {
        /**
         * Start a tap of [source] in [scope].
         *
         * `Dispatchers.Unconfined` is load-bearing, not a micro-
         * optimisation: the collector runs inline on the emitting
         * Bluetooth binder thread, so (a) the subscription is already
         * live by the time this function returns, and (b) a chunk is in
         * [inbox] before `onCharacteristicChanged` returns rather than
         * after a dispatch that may be delayed behind main-thread work.
         */
        internal fun <T> start(scope: CoroutineScope, source: SharedFlow<T>): ReceiveTap<T> {
            val inbox = Channel<T>(Channel.UNLIMITED)
            val tap = scope.launch(Dispatchers.Unconfined) {
                try {
                    source.collect { inbox.send(it) }
                } catch (_: CancellationException) {
                    // stop() — the normal end of a tap's life.
                } finally {
                    inbox.close()
                }
            }
            return ReceiveTap(inbox, tap)
        }
    }
}

/** Convenience: `scope.tapSharedFlow(controller.fa12Notifications)`. */
internal fun <T> CoroutineScope.tapSharedFlow(source: SharedFlow<T>): ReceiveTap<T> =
    ReceiveTap.start(this, source)
