package io.github.santimattius.structured.runtime

private const val IO_PARALLELISM_PROPERTY = "kotlinx.coroutines.io.parallelism"

internal actual fun inferredDefaultParallelism(): Int? =
    maxOf(2, Runtime.getRuntime().availableProcessors())

internal actual fun inferredIoParallelism(): Int? {
    val configured = try {
        System.getProperty(IO_PARALLELISM_PROPERTY)?.toIntOrNull()
    } catch (_: SecurityException) {
        null
    }
    return if (configured != null && configured > 0) {
        configured
    } else {
        maxOf(64, Runtime.getRuntime().availableProcessors())
    }
}
