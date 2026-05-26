package io.github.santimattius.structured.gradle

/**
 * How baseline entries affect reported severity.
 */
enum class BaselineMode {
    /** Existing baseline hits are INFO; new violations keep configured severity. */
    REPORT_NEW_ONLY,
    /** All violations use configured severity (baseline only used for generate task). */
    FULL,
}
