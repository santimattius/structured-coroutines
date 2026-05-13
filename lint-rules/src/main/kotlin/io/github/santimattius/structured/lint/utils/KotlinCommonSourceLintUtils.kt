/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.utils

/**
 * Mirrors Detekt [`CoroutineDetektUtils.isKotlinCommonLikeSourceVirtualPath`]: KMP folders where
 * [kotlinx.coroutines.Dispatchers.IO] is unsupported on Kotlin/Native/JS.
 */
object KotlinCommonSourceLintUtils {

    fun absolutePathLooksLikeKotlinCommonLikeSource(filePath: String): Boolean {
        val normalized = filePath.replace('\\', '/')
        return normalized.contains("/commonMain/") || normalized.contains("/commonTest/")
    }
}
