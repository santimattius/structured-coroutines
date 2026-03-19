/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Fast import-based guard to determine whether a file uses kotlinx.coroutines APIs.
 *
 * Detekt rules operate on PSI without type resolution. Using this guard as an early
 * exit prevents false positives when non-coroutines code happens to use function
 * names that overlap with the kotlinx.coroutines API (e.g. a custom `async {}`,
 * `launch {}`, or a `Dispatchers` object in a different library).
 */
object CoroutinesImportFilter {

    private const val KOTLINX_COROUTINES_PREFIX = "kotlinx.coroutines"

    /**
     * Returns true if the file contains at least one import from `kotlinx.coroutines`.
     */
    fun fileImportsCoroutines(file: KtFile): Boolean {
        return file.importDirectives.any { directive ->
            directive.importedFqName?.asString()?.startsWith(KOTLINX_COROUTINES_PREFIX) == true
        }
    }

    /**
     * Convenience overload: extracts the [KtFile] from any [KtElement].
     */
    fun elementIsInCoroutinesFile(element: KtElement): Boolean {
        return fileImportsCoroutines(element.containingKtFile)
    }
}
