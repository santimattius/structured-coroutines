package io.github.santimattius.structured.runtime

import android.os.Trace

/**
 * Placeholder that only proves the Android platform API (`android.os.Trace`) resolves from
 * the `:runtime` Android target. It is not part of the public API and will be replaced by
 * the Android trace sink.
 */
internal class AndroidTraceProbe {
    fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}
