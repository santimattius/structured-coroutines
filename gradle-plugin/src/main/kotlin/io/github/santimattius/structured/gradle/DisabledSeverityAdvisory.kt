/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.gradle

/**
 * Builds the build-time advisory message shown when one or more Structured Coroutines checks
 * are configured with severity `"disabled"`.
 *
 * `"disabled"` is decorative this release: it is recorded and reflected in
 * [StructuredCoroutinesReportTask], but diagnostics for the affected check are still reported at
 * the rule's default severity, because no [org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor]
 * is registered to carry the DSL value into the compiler plugin. Real compile-time suppression is
 * tracked in [TRACKING_ISSUE].
 *
 * This is a pure, `Project`-free function so the advisory content is testable without a
 * `ProjectBuilder`/TestKit round-trip — see design ADR-5.
 */
internal object DisabledSeverityAdvisory {

    const val TRACKING_ISSUE = "https://github.com/santimattius/structured-coroutines/issues/68"

    /**
     * Returns the advisory warning text naming every disabled check in [disabledKeys], or `null`
     * when the list is empty (nothing to warn about).
     */
    fun message(disabledKeys: List<String>): String? {
        if (disabledKeys.isEmpty()) return null

        val keys = disabledKeys.joinToString(", ")
        return "Structured Coroutines: the following check(s) are set to \"disabled\" and will " +
            "still be reported at their default severity this release — \"disabled\" does not yet " +
            "suppress diagnostics: $keys. Real compile-time enforcement is tracked in $TRACKING_ISSUE."
    }
}
