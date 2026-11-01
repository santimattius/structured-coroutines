package io.github.santimattius.structured.runtime

/**
 * Marks declarations in the `:runtime` optional companion library that are still experimental.
 *
 * Any usage of a declaration annotated with `@ExperimentalStructuredCoroutines` must be accepted
 * either by annotating the usage with [OptIn], e.g. `@OptIn(ExperimentalStructuredCoroutines::class)`,
 * or by using the compiler argument `-opt-in=io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines`.
 *
 * The opt-in level is [RequiresOptIn.Level.WARNING]: unaccepted usages compile with a warning rather
 * than an error. The gate is intended to be lifted once the `:runtime` API stabilises beyond 0.x.
 *
 * Example:
 * ```
 * @OptIn(ExperimentalStructuredCoroutines::class)
 * suspend fun load() = runCatchingCancellable { api.fetch() }
 * ```
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This structured-coroutines runtime API is experimental and may change or be " +
        "removed without notice while :runtime is on the 0.x line.",
)
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalStructuredCoroutines
