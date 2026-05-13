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
 * Unit tests for [SuspendCoroutineWithoutCancellationInspection].
 *
 * IntelliJ Platform integration tests require a running IDE environment with JDK 21.
 * These tests verify the inspection's metadata and registration properties, which
 * can be validated without a running IDE.
 *
 * TDD Evidence: RED — these tests are written before the implementation class exists.
 * GREEN — tests pass once SuspendCoroutineWithoutCancellationInspection is implemented.
 */
class SuspendCoroutineWithoutCancellationInspectionTest {

    @Test
    fun `inspection can be instantiated`() {
        val inspection = SuspendCoroutineWithoutCancellationInspection()
        assertThat(inspection).isNotNull()
    }

    @Test
    fun `inspection is enabled by default`() {
        val inspection = SuspendCoroutineWithoutCancellationInspection()
        assertThat(inspection.isEnabledByDefault).isTrue()
    }

    @Test
    fun `inspection display name key is set`() {
        val inspection = SuspendCoroutineWithoutCancellationInspection()
        assertThat(inspection.displayNameKey).isNotBlank()
        assertThat(inspection.displayNameKey).isEqualTo("inspection.suspend.coroutine.without.cancellation.display.name")
    }

    @Test
    fun `inspection description key is set`() {
        val inspection = SuspendCoroutineWithoutCancellationInspection()
        assertThat(inspection.descriptionKey).isNotBlank()
        assertThat(inspection.descriptionKey).isEqualTo("inspection.suspend.coroutine.without.cancellation.description")
    }
}
