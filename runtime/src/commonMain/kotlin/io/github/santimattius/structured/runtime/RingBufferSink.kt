package io.github.santimattius.structured.runtime

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Keeps the last [capacity] events, for tests, crash reports and ad-hoc export.
 *
 * Lock-free and safe for concurrent [emit] from many threads. Implementation: a sequence counter
 * claims a slot (`seq % capacity`) among `capacity` [AtomicReference]s (the common stdlib has no
 * atomic array primitive); the slot holds an immutable `(seq, event)`
 * pair and is replaced by compare-and-set only when the stored sequence is older, so a delayed
 * writer can never overwrite a newer event. [snapshot] reads the slots and orders by sequence.
 * This avoids copying the whole buffer on every emit (which a CAS loop over one immutable
 * snapshot would do). An event whose slot is claimed but not yet written may be briefly absent
 * from a concurrent snapshot, which is acceptable for a diagnostic sink.
 *
 * Example:
 * ```
 * val ring = RingBufferSink(capacity = 256)
 * // ... run traced work ...
 * ring.snapshot().forEach(::println)
 * ```
 *
 * Once more than [capacity] events were emitted, the oldest ones are overwritten.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param capacity maximum number of retained events; must be positive.
 * @param maxLevel highest [TraceLevel] this sink accepts.
 * @throws IllegalArgumentException if [capacity] is not positive.
 * @see StatsSink
 */
@OptIn(ExperimentalAtomicApi::class)
@ExperimentalStructuredCoroutines
public class RingBufferSink(
    public val capacity: Int,
    public val maxLevel: TraceLevel = TraceLevel.Pool,
) : TraceSink {

    private class Entry(val seq: Long, val event: TraceEvent)

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val slots = Array(capacity) { AtomicReference<Entry?>(null) }
    private val sequence = AtomicLong(0L)

    // Entries with seq <= floor are considered cleared.
    private val floor = AtomicLong(0L)

    override fun isEnabled(level: TraceLevel): Boolean = level <= maxLevel

    override fun emit(event: TraceEvent) {
        val seq = sequence.addAndFetch(1L)
        val index = ((seq - 1) % capacity).toInt()
        val entry = Entry(seq, event)
        while (true) {
            val current = slots[index].load()
            if (current != null && current.seq > seq) return
            if (slots[index].compareAndSet(current, entry)) return
        }
    }

    /**
     * Returns a copy of the retained events, oldest first (at most [capacity] elements). The list
     * is not updated by later events.
     */
    public fun snapshot(): List<TraceEvent> {
        val cleared = floor.load()
        val entries = ArrayList<Entry>(capacity)
        for (i in 0 until capacity) {
            val e = slots[i].load()
            if (e != null && e.seq > cleared) entries.add(e)
        }
        entries.sortBy { it.seq }
        return entries.map { it.event }
    }

    /** Drops every retained event. Events emitted concurrently with the call may survive it. */
    public fun clear() {
        floor.store(sequence.load())
    }
}
