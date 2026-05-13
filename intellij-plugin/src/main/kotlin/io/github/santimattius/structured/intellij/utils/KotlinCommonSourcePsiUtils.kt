/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.intellij.utils

/**
 * KMP-aware virtual paths aligned with Lint [io.github.santimattius.structured.lint.utils.KotlinCommonSourceLintUtils].
 */
object KotlinCommonSourcePsiUtils {

    fun looksLikeKotlinCommonVirtualPath(virtualPath: String): Boolean {
        val normalized = virtualPath.replace('\\', '/')
        return normalized.contains("/commonMain/") || normalized.contains("/commonTest/")
    }
}
