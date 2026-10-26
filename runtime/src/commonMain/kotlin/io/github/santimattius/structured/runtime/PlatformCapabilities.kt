package io.github.santimattius.structured.runtime

/**
 * What the current target can do for tracing out of the box, as shipped in `:runtime`. This is the
 * extension seam for new platforms: a target compiles and works with every flag false, and gains
 * fidelity when someone writes its `actual` of [platformCapabilities] and [platformTraceSink].
 *
 * A missing capability degrades, it never fails: a sink without thread identity omits it, and one
 * that cannot draw flows ignores `parentSpanId`. A plain final class (not a data class) so flags
 * can be added later without a binary-incompatible `copy`/`componentN` change.
 *
 * Example:
 * ```
 * if (platformCapabilities.counters) println("pending/running tracks are available")
 * ```
 *
 * @property threadIdentity run events carry a real [ThreadInfo] on this target.
 * @property systemTraceIntegration `:runtime` ships a sink for this target's system tracer (see
 *   [platformTraceSink]). Backends in separate artifacts, such as `:runtime-tracing-jvm`, do not count.
 * @property counters that default sink publishes `pending`/`running` counter tracks.
 * @property flows that default sink draws span links from `parentSpanId`.
 */
@ExperimentalStructuredCoroutines
public class TraceCapabilities(
    public val threadIdentity: Boolean,
    public val systemTraceIntegration: Boolean,
    public val counters: Boolean,
    public val flows: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is TraceCapabilities &&
            threadIdentity == other.threadIdentity &&
            systemTraceIntegration == other.systemTraceIntegration &&
            counters == other.counters &&
            flows == other.flows)

    override fun hashCode(): Int {
        var h = threadIdentity.hashCode()
        h = 31 * h + systemTraceIntegration.hashCode()
        h = 31 * h + counters.hashCode()
        h = 31 * h + flows.hashCode()
        return h
    }

    override fun toString(): String =
        "TraceCapabilities(threadIdentity=$threadIdentity, systemTraceIntegration=$systemTraceIntegration, " +
            "counters=$counters, flows=$flows)"
}

/**
 * The tracing capabilities of the current target. Truthful, never throws; all flags are false on
 * targets without a backend.
 *
 * Example:
 * ```
 * val canShowThreads = platformCapabilities.threadIdentity
 * ```
 */
@ExperimentalStructuredCoroutines
public expect val platformCapabilities: TraceCapabilities

/**
 * The default system-trace sink for the current target, or null when `:runtime` ships none (every
 * target except Android). Null is the degraded answer, not an error: compose your own sinks.
 *
 * Example:
 * ```
 * val sink = platformTraceSink() ?: LoggingSink(log = ::println)
 * val dispatchers = DefaultDispatcherProvider.traced(sink)
 * ```
 */
@ExperimentalStructuredCoroutines
public expect fun platformTraceSink(): TraceSink?
