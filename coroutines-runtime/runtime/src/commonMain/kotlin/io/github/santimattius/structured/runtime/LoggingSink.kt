package io.github.santimattius.structured.runtime

/**
 * Writes each event as one human-readable line (its `toString`) to [log].
 * Never throws: an [Exception] from [log] is swallowed so logging cannot break dispatch.
 *
 * Example:
 * ```
 * val sink = LoggingSink(TraceLevel.Coroutine) { line -> println(line) }
 * ```
 *
 * Thread-safety is that of [log]: [emit] may be called concurrently from many threads.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param maxLevel highest [TraceLevel] this sink accepts.
 * @param log receives one line per event; must be cheap and non-blocking.
 * @see CompositeSink
 */
@ExperimentalStructuredCoroutines
public class LoggingSink(
    public val maxLevel: TraceLevel = TraceLevel.Dispatch,
    private val log: (String) -> Unit,
) : TraceSink {

    override fun isEnabled(level: TraceLevel): Boolean = level <= maxLevel

    override fun emit(event: TraceEvent) {
        try {
            log(event.toString())
        } catch (_: Exception) {
            // A faulty logger must never break the caller.
        }
    }
}
