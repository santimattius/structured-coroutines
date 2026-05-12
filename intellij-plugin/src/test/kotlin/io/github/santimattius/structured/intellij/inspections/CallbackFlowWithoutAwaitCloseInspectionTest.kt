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
 * Unit tests for [CallbackFlowWithoutAwaitCloseInspection].
 *
 * TDD Evidence: RED — tests written before implementation exists.
 * GREEN — tests pass once CallbackFlowWithoutAwaitCloseInspection is implemented.
 */
class CallbackFlowWithoutAwaitCloseInspectionTest {

    @Test
    fun `inspection can be instantiated`() {
        val inspection = CallbackFlowWithoutAwaitCloseInspection()
        assertThat(inspection).isNotNull()
    }

    @Test
    fun `inspection is enabled by default`() {
        val inspection = CallbackFlowWithoutAwaitCloseInspection()
        assertThat(inspection.isEnabledByDefault).isTrue()
    }

    @Test
    fun `inspection display name key is set`() {
        val inspection = CallbackFlowWithoutAwaitCloseInspection()
        assertThat(inspection.displayNameKey).isEqualTo("inspection.callback.flow.without.await.close.display.name")
    }

    @Test
    fun `inspection description key is set`() {
        val inspection = CallbackFlowWithoutAwaitCloseInspection()
        assertThat(inspection.descriptionKey).isEqualTo("inspection.callback.flow.without.await.close.description")
    }
}
