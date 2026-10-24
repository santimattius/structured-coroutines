package io.github.santimattius.structured.runtime

/**
 * Inferred concurrency limit of `Dispatchers.Default`, or null when there is no verified source on
 * this platform. Inferred from documented defaults, not read from the live scheduler.
 */
internal expect fun inferredDefaultParallelism(): Int?

/**
 * Inferred concurrency limit of the IO dispatcher ([ioCompat][kotlinx.coroutines.Dispatchers]), or
 * null when there is no verified source on this platform.
 */
internal expect fun inferredIoParallelism(): Int?
