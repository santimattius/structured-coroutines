package io.github.santimattius.structured.annotations

@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD
)
@Retention(AnnotationRetention.BINARY)
annotation class StructuredScope
