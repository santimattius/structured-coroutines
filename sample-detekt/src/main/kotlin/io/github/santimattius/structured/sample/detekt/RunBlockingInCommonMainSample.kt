package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.runBlocking

/**
 * Documents KMP_002: [runBlocking] in commonMain.
 *
 * Real violations are detected in KMP `commonMain` source sets (see detekt-rules test resources).
 * This JVM sample illustrates the anti-pattern for local exploration.
 */

// BAD: runBlocking in shared KMP code — deadlock on iOS main thread; missing on JS
fun badLoadSync(): Int = runBlocking {
    42
}
