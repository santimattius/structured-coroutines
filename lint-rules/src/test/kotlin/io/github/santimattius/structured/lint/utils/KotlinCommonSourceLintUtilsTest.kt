/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.utils

import io.github.santimattius.structured.lint.detectors.DispatchersIOInCommonMainDetector
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KotlinCommonSourceLintUtilsTest {

    @Test
    fun `recognizes unix commonMain`() {
        assertThat(
            KotlinCommonSourceLintUtils.absolutePathLooksLikeKotlinCommonLikeSource("/p/shared/src/commonMain/kotlin/A.kt"),
        ).isTrue()
    }

    @Test
    fun `recognizes windows commonTest`() {
        assertThat(
            KotlinCommonSourceLintUtils.absolutePathLooksLikeKotlinCommonLikeSource("C:\\m\\src\\commonTest\\kotlin\\A.kt"),
        ).isTrue()
    }

    @Test
    fun `jvm main is excluded`() {
        assertThat(
            KotlinCommonSourceLintUtils.absolutePathLooksLikeKotlinCommonLikeSource("/p/app/src/main/java/A.kt"),
        ).isFalse()
    }

    @Test
    fun `dispatchers io access strings`() {
        assertThat(DispatchersIOInCommonMainDetector.isDispatchersIOAccess("Dispatchers.IO")).isTrue()
        assertThat(DispatchersIOInCommonMainDetector.isDispatchersIOAccess("kotlinx.coroutines.Dispatchers.IO")).isTrue()
        assertThat(DispatchersIOInCommonMainDetector.isDispatchersIOAccess("Dispatchers.Default")).isFalse()
    }
}
