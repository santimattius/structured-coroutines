package io.github.santimattius.structured.runtime

/**
 * Marks declarations in the `:runtime` optional companion library that are still experimental.
 *
 * Any usage of a declaration annotated with `@ExperimentalStructuredCoroutines` must be accepted
 * either by annotating the usage with [OptIn], e.g. `@OptIn(ExperimentalStructuredCoroutines::class)`,
 * or by using the compiler argument `-opt-in=io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines`.
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
