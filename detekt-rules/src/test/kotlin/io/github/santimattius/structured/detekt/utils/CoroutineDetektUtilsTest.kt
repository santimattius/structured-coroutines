/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoroutineDetektUtilsTest {

    @Test
    fun `detects commonMain paths`() {
        assertThat(
            CoroutineDetektUtils.isKotlinCommonLikeSourceVirtualPath(
                "/project/shared/src/commonMain/kotlin/Foo.kt",
            ),
        ).isTrue()
    }

    @Test
    fun `detects commonTest paths`() {
        assertThat(
            CoroutineDetektUtils.isKotlinCommonLikeSourceVirtualPath(
                "C:\\project\\shared\\src\\commonTest\\kotlin\\Foo.kt",
            ),
        ).isTrue()
    }

    @Test
    fun `jvmMain is not common-like`() {
        assertThat(
            CoroutineDetektUtils.isKotlinCommonLikeSourceVirtualPath(
                "/project/app/src/jvmMain/kotlin/Foo.kt",
            ),
        ).isFalse()
    }
}
