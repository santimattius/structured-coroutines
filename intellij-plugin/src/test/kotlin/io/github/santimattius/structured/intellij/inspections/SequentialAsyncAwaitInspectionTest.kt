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
 * Unit tests for [SequentialAsyncAwaitInspection].
 *
 * TDD Evidence: RED — tests written before implementation exists.
 * GREEN — tests pass once SequentialAsyncAwaitInspection is implemented.
 */
class SequentialAsyncAwaitInspectionTest {

    @Test
    fun `inspection can be instantiated`() {
        val inspection = SequentialAsyncAwaitInspection()
        assertThat(inspection).isNotNull()
    }

    @Test
    fun `inspection is enabled by default`() {
        val inspection = SequentialAsyncAwaitInspection()
        assertThat(inspection.isEnabledByDefault).isTrue()
    }

    @Test
    fun `inspection display name key is set`() {
        val inspection = SequentialAsyncAwaitInspection()
        assertThat(inspection.displayNameKey).isEqualTo("inspection.sequential.async.await.display.name")
    }

    @Test
    fun `inspection description key is set`() {
        val inspection = SequentialAsyncAwaitInspection()
        assertThat(inspection.descriptionKey).isEqualTo("inspection.sequential.async.await.description")
    }
}
