/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.utils

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

fun KtFile.importsKotlinxCoroutines(): Boolean =
    importDirectives.any {
        it.importedFqName?.asString()?.startsWith("kotlinx.coroutines") == true
    }

fun KtNamedFunction.hasShortNameTestAnnotation(): Boolean =
    annotationEntries.any { it.shortName?.asString() == "Test" }
