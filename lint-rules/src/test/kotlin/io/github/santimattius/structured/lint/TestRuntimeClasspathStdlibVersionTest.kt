/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Guard test asserting `:lint-rules`' `testRuntimeClasspath` resolves `kotlin-stdlib` to the
 * version declared by `gradle/libs.versions.toml`'s `kotlin` catalog entry, not to whatever
 * version `lint-rules/build.gradle.kts`'s `testRuntimeClasspath` `resolutionStrategy.force(...)`
 * pins it to. If this test fails, either the `force(...)` override is still present and pinning
 * the stdlib away from the catalog, or the resolved Android Lint dependency graph pulls a stdlib
 * version other than the one the rest of the project builds against.
 */
class TestRuntimeClasspathStdlibVersionTest {

    @Test
    fun `testRuntimeClasspath resolves kotlin-stdlib to the catalog kotlin version`() {
        val catalogKotlinVersion = System.getProperty("catalogKotlinVersion")
            ?: error("catalogKotlinVersion system property not set — run via Gradle (:lint-rules:test)")
        val resolvedKotlinStdlibVersion = System.getProperty("resolvedKotlinStdlibVersion")
            ?: error("resolvedKotlinStdlibVersion system property not set — check lint-rules/build.gradle.kts tasks.test block")

        assertThat(resolvedKotlinStdlibVersion)
            .describedAs(
                "testRuntimeClasspath resolved kotlin-stdlib=%s but the catalog kotlin=%s; " +
                    "lint-rules/build.gradle.kts's testRuntimeClasspath resolutionStrategy is " +
                    "overriding the stdlib version away from the catalog.",
                resolvedKotlinStdlibVersion,
                catalogKotlinVersion,
            )
            .isEqualTo(catalogKotlinVersion)
    }
}
