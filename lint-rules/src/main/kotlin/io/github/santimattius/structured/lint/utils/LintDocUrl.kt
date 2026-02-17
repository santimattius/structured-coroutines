/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint.utils

object LintDocUrl {
    const val DOC_BASE_URL =
        "https://github.com/santimattius/structured-coroutines/blob/main/docs/BEST_PRACTICES_COROUTINES.md"

    fun buildDocLink(anchor: String): String = "$DOC_BASE_URL#$anchor"
}
