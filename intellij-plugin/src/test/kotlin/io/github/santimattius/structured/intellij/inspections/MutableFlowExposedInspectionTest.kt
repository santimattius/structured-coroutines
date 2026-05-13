/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MutableFlowExposedInspection].
 *
 * TDD Evidence: RED — tests written before implementation exists.
 * GREEN — tests pass once MutableFlowExposedInspection is implemented.
 */
class MutableFlowExposedInspectionTest {

    @Test
    fun `inspection can be instantiated`() {
        val inspection = MutableFlowExposedInspection()
        assertThat(inspection).isNotNull()
    }

    @Test
    fun `inspection is enabled by default`() {
        val inspection = MutableFlowExposedInspection()
        assertThat(inspection.isEnabledByDefault).isTrue()
    }

    @Test
    fun `inspection display name key is set`() {
        val inspection = MutableFlowExposedInspection()
        assertThat(inspection.displayNameKey).isEqualTo("inspection.mutable.flow.exposed.display.name")
    }

    @Test
    fun `inspection description key is set`() {
        val inspection = MutableFlowExposedInspection()
        assertThat(inspection.descriptionKey).isEqualTo("inspection.mutable.flow.exposed.description")
    }
}
