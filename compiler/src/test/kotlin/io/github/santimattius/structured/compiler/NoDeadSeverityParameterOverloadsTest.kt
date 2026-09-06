/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.Severity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 2.8 (severity-enforcement, #68, ADR-5): guards against a dead
 * `reportXxx(call, context, severity: Severity)` overload ever reappearing.
 *
 * Before PR2, `StructuredCoroutinesErrors.kt:408-437` had 3 such overloads
 * (`reportUnstructuredLaunch`, `reportGlobalScopeUsage`, `reportInlineCoroutineScope`) with zero
 * call sites — dead code that pretended severity was caller-supplied when it is always *resolved*
 * from [PluginConfiguration]. All 14 `reportXxx` extension functions in
 * `StructuredCoroutinesErrors.kt` now take a `config: PluginConfiguration` parameter instead;
 * none should ever take a raw [Severity] again, because `Severity` cannot represent the
 * `DISABLED` tri-state (ADR-2) — reintroducing such an overload would silently regress "disabled"
 * enforcement for whichever call site used it.
 *
 * This inspects the compiled file-facade class (`StructuredCoroutinesErrorsKt`, the JVM class
 * Kotlin generates for this file's top-level functions) via reflection rather than scanning
 * source text, so the invariant holds regardless of how the file is reformatted.
 */
class NoDeadSeverityParameterOverloadsTest {

    @Test
    fun `no reportXxx function takes a raw Severity parameter`() {
        val facadeClass = Class.forName("io.github.santimattius.structured.compiler.StructuredCoroutinesErrorsKt")

        val offendingMethods = facadeClass.declaredMethods
            .filter { it.name.startsWith("report") }
            .filter { method -> method.parameterTypes.any { it == Severity::class.java } }
            .map { it.name }

        assertTrue(
            offendingMethods.isEmpty(),
            "Expected no reportXxx function to take a raw Severity parameter, but found: $offendingMethods",
        )
    }
}
