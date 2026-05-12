package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Demonstrates CONCUR_003: Sequential async/await (wasted parallelism).
 * Used to validate that :detekt-rules report async { }.await() inline patterns
 * that provide no parallelism benefit.
 *
 * BAD: async { }.await() in sequence is semantically equivalent to withContext { }
 * but creates unnecessary Deferred overhead without achieving parallelism.
 */

data class Dashboard(val user: String, val metrics: String)

suspend fun fetchUser(): String = "user"
suspend fun fetchMetrics(): String = "metrics"

// BAD: sequential — no parallelism benefit
suspend fun badLoadDashboard(): Dashboard = coroutineScope {
    val user = async { fetchUser() }.await()
    val metrics = async { fetchMetrics() }.await()
    Dashboard(user, metrics)
}

// GOOD: parallel async
suspend fun goodLoadDashboard(): Dashboard = coroutineScope {
    val userDeferred = async { fetchUser() }
    val metricsDeferred = async { fetchMetrics() }
    Dashboard(userDeferred.await(), metricsDeferred.await())
}
