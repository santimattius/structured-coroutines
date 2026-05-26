package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*
import org.slf4j.MDC

/** Triggers BACKEND_002 when slf4j MDC is on classpath. */
suspend fun threadLocalMdcSample() {
    MDC.put("k", "v")
    withContext(Dispatchers.IO) { MDC.get("k") }
}
