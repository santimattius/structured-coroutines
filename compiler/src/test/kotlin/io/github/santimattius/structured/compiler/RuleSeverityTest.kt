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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Task 1.3/1.4 (severity-enforcement, #68, ADR-2): [RuleSeverity] is the tri-state severity
 * representation (`disabled`/`warning`/`error`) that a plain [Severity] cannot express, since
 * `Severity` has no disabled/off member.
 *
 * [RuleSeverity.rank] MUST be an explicit constructor value, never `ordinal` — ADR-7's
 * relax/tighten direction rule (`configured.rank` vs `default.rank`) depends on rank being
 * stable even if entries are reordered.
 */
class RuleSeverityTest {

    @Test
    fun `ranks are DISABLED0, WARNING1, ERROR2`() {
        assertEquals(0, RuleSeverity.DISABLED.rank)
        assertEquals(1, RuleSeverity.WARNING.rank)
        assertEquals(2, RuleSeverity.ERROR.rank)
    }

    @Test
    fun `rank strictly increases from DISABLED to WARNING to ERROR`() {
        assertEquals(
            listOf(RuleSeverity.DISABLED, RuleSeverity.WARNING, RuleSeverity.ERROR),
            listOf(RuleSeverity.DISABLED, RuleSeverity.WARNING, RuleSeverity.ERROR)
                .sortedBy { it.rank },
        )
    }

    @Test
    fun `toDiagnostic maps DISABLED to null since Severity has no disabled member`() {
        assertNull(RuleSeverity.DISABLED.toDiagnostic())
    }

    @Test
    fun `toDiagnostic maps WARNING and ERROR to the matching Severity`() {
        assertEquals(Severity.WARNING, RuleSeverity.WARNING.toDiagnostic())
        assertEquals(Severity.ERROR, RuleSeverity.ERROR.toDiagnostic())
    }
}
