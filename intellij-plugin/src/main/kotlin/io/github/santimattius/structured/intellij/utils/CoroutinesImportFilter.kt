/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * Fast import-based guard to determine whether a file uses kotlinx.coroutines APIs.
 *
 * Used as an early-exit in all inspections: if the file does not import anything from
 * `kotlinx.coroutines`, no coroutines-specific inspection should fire on it.
 * This eliminates false positives on non-coroutines code that happens to use function
 * names shared with the library (e.g. `ActivityScenario.launch`, custom `async`, etc.).
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
     * Convenience overload that extracts the [KtFile] from the call expression.
     */
    fun callIsInCoroutinesFile(call: KtCallExpression): Boolean {
        return fileImportsCoroutines(call.containingKtFile)
    }
}
