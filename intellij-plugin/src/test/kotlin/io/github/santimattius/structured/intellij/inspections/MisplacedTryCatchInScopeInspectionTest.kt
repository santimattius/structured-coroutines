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
 * Metadata tests for [MisplacedTryCatchInScopeInspection] (EXCEPT_004).
 *
 * These tests do not exercise the PSI visitor: this module has no PSI fixture test
 * infrastructure (no BasePlatformTestCase/CodeInsightTestFixture usage).
 *
 * TDD Evidence: RED — written before the implementation exists (unresolved reference).
 */
class MisplacedTryCatchInScopeInspectionTest {

    @Test
    fun `inspection can be instantiated`() {
        assertThat(MisplacedTryCatchInScopeInspection()).isNotNull()
    }

    @Test
    fun `inspection is enabled by default`() {
        assertThat(MisplacedTryCatchInScopeInspection().isEnabledByDefault).isTrue()
    }

    @Test
    fun `inspection short name matches the rule manifest`() {
        assertThat(MisplacedTryCatchInScopeInspection().shortName).isEqualTo("MisplacedTryCatchInScope")
    }

    @Test
    fun `inspection display name key is set`() {
        assertThat(MisplacedTryCatchInScopeInspection().displayNameKey)
            .isEqualTo("inspection.misplaced.try.catch.in.scope.display.name")
    }

    @Test
    fun `inspection description key is set`() {
        assertThat(MisplacedTryCatchInScopeInspection().descriptionKey)
            .isEqualTo("inspection.misplaced.try.catch.in.scope.description")
    }
}
