package io.github.santimattius.structured.annotations

/**
 * Qualifies a [kotlinx.coroutines.CoroutineDispatcher] intended for blocking I/O work.
 * Use with dependency injection so production code uses [kotlinx.coroutines.Dispatchers.IO]
 * and tests can substitute an unconfined or test dispatcher.
 */
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
)
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifies a [kotlinx.coroutines.CoroutineDispatcher] bound to the UI / main thread.
 */
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
)
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Qualifies a [kotlinx.coroutines.CoroutineDispatcher] for CPU-bound default work.
 */
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
)
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
