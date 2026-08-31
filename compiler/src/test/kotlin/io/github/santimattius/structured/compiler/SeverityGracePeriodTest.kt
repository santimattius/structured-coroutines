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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 3.1/3.2 (severity-enforcement, #68, ADR-7): [SeverityGracePeriod] carries the grace-period
 * defaults — [SeverityGracePeriod.DEFAULT_POLICY] ships as [EnforcementPolicy.GRACE] in 1.2.0, and
 * [SeverityGracePeriod.ENFORCING_VERSION] names the release (1.3.0) where tightening starts
 * enforcing. The advisory message MUST reference [SeverityGracePeriod.ENFORCING_VERSION] directly
 * rather than a duplicated literal, so a half-done 1.3.0 flip (bumping the constant but not the
 * message, or vice versa) cannot silently drift.
 */
class SeverityGracePeriodTest {

    @Test
    fun `DEFAULT_POLICY is GRACE`() {
        assertEquals(EnforcementPolicy.GRACE, SeverityGracePeriod.DEFAULT_POLICY)
    }

    @Test
    fun `ENFORCING_VERSION is 1_3_0`() {
        assertEquals("1.3.0", SeverityGracePeriod.ENFORCING_VERSION)
    }

    @Test
    fun `advisory message for a deferred tightening references ENFORCING_VERSION, not a duplicated literal`() {
        val tightening = DeferredTightening(
            rule = ScoroutinesRule.LOOP_WITHOUT_YIELD,
            configured = RuleSeverity.ERROR,
            effective = RuleSeverity.WARNING,
        )
        val message = tightening.advisoryText()
        assertTrue(
            SeverityGracePeriod.ENFORCING_VERSION in message,
            "Advisory message must reference SeverityGracePeriod.ENFORCING_VERSION, got:\n$message",
        )
        assertTrue("loopWithoutYield" in message, "Advisory message must name the rule, got:\n$message")
        assertTrue("error" in message.lowercase(), "Advisory message must name the configured value, got:\n$message")
        assertTrue("warning" in message.lowercase(), "Advisory message must name the currently-effective severity, got:\n$message")
    }
}
