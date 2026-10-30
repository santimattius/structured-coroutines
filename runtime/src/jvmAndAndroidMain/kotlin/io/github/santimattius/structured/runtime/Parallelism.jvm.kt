package io.github.santimattius.structured.runtime

private const val IO_PARALLELISM_PROPERTY = "kotlinx.coroutines.io.parallelism"
private const val CORE_POOL_SIZE_PROPERTY = "kotlinx.coroutines.scheduler.core.pool.size"

private fun positiveIntProperty(name: String): Int? {
    val value = try {
        System.getProperty(name)?.toIntOrNull()
    } catch (_: SecurityException) {
        null
    }
    return value?.takeIf { it > 0 }
}

// kotlinx reads these properties once, when its scheduler is first used; this reads them on every
// call, so the result can differ if a property is changed after the scheduler started.
internal actual fun inferredDefaultParallelism(): Int? =
    positiveIntProperty(CORE_POOL_SIZE_PROPERTY) ?: maxOf(2, Runtime.getRuntime().availableProcessors())

internal actual fun inferredIoParallelism(): Int? =
    positiveIntProperty(IO_PARALLELISM_PROPERTY) ?: maxOf(64, Runtime.getRuntime().availableProcessors())
