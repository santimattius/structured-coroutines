package io.github.santimattius.structured.runtime.tracing

import androidx.tracing.Tracer
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.createPerfettoFile
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceLevel
import okio.appendingSink
import okio.buffer
import java.io.File
import androidx.tracing.wire.TraceSink as WireTraceSink

/**
 * An in-process Perfetto trace written to a new `.perfetto-trace` file, with a [PerfettoTraceSink]
 * already wired to it. Start one, hand [sink] to `DispatcherProvider.traced`, and [close] it when
 * done; the file then opens in [ui.perfetto.dev](https://ui.perfetto.dev).
 *
 * The session owns an `androidx.tracing` driver and tracer of its own and never installs them as
 * the global tracer (`Tracer.setGlobalTracer`); pass [tracer] to other code explicitly if you want
 * it to write to the same file. Events are serialized on a background thread, so the file is only
 * complete after [flush] (which drains what was emitted so far) or [close].
 *
 * Sessions are not reusable: after [close] the sink silently drops events. [flush] and [close]
 * are safe to call concurrently; they are serialized, and [close] is idempotent. Experimental,
 * diagnostic and opt-in.
 *
 * Platform availability: JVM only (this artifact is `kotlin("jvm")`). On Android use `AndroidTraceSink`
 * from `:runtime`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * Example:
 * ```
 * PerfettoTraceSession.start(File("build/traces")).use { session ->
 *     val provider = DefaultDispatcherProvider.traced(session.sink)
 *     runBlocking { withContext(provider.io + CoroutineName("fetch")) { api.fetch() } }
 * }
 * ```
 *
 * @see PerfettoTraceSink
 */
@ExperimentalStructuredCoroutines
public class PerfettoTraceSession private constructor(
    /** The trace file this session writes to. */
    public val file: File,
    private val driver: TraceDriver,
    maxLevel: TraceLevel,
) : AutoCloseable {
    /** The tracer owned by this session. It is not the global tracer. */
    public val tracer: Tracer = driver.tracer

    /** The sink that writes to [tracer]; pass it to `DispatcherProvider.traced`. */
    public val sink: PerfettoTraceSink = PerfettoTraceSink(tracer, maxLevel)

    private val lifecycleLock = Any()
    private var closed = false

    /** Writes everything emitted so far to [file]. A no-op once the session is closed. */
    public fun flush() {
        synchronized(lifecycleLock) {
            if (!closed) driver.flush()
        }
    }

    /** Flushes and closes [file]. Serialized with [flush] and idempotent; the sink drops events afterwards. */
    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            sink.disable()
            driver.close()
        }
    }

    /** Factory for [PerfettoTraceSession]. */
    public companion object {
        /**
         * Starts a session that writes a new `perfetto-<timestamp>-<n>.perfetto-trace` file in
         * [directory], creating the directory when needed.
         *
         * @param directory where the trace file is created.
         * @param maxLevel highest [TraceLevel] the sink accepts.
         * @return the started session; the caller must [close] it.
         */
        @JvmOverloads
        public fun start(
            directory: File,
            maxLevel: TraceLevel = TraceLevel.Coroutine,
        ): PerfettoTraceSession {
            directory.mkdirs()
            val file = directory.createPerfettoFile()
            val wireSink =
                WireTraceSink(
                    sequenceId = 1,
                    bufferedSink = file.appendingSink().buffer(),
                    coroutineContext = kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable,
                )
            return PerfettoTraceSession(file, TraceDriver(sink = wireSink), maxLevel)
        }
    }
}
