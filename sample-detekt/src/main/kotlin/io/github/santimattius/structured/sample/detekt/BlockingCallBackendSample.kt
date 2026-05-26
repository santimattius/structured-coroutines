package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*

/** Triggers BACKEND_001 — Thread.sleep in suspend without IO context. */
suspend fun blockingBackendSample() {
    Thread.sleep(1)
}
