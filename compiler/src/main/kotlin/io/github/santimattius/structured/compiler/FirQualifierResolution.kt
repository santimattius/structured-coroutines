/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Reconstructs a [ClassId] from a [FirResolvedQualifier]'s two stable parts, mirroring
 * JetBrains' own `FirExpressionUtil.kt` extension-property logic (#90).
 *
 * Pure core: unit-testable without constructing a real `FirResolvedQualifier`, which this
 * codebase's test suite has no precedent for (no FIR node is ever mocked/built in tests).
 *
 * @param packageFqName the qualifier's package, e.g. `kotlinx.coroutines`
 * @param relativeClassFqName the class name(s) relative to the package, e.g. `NonCancellable`
 *   or `Outer.Inner` for a nested class; `null` for a package-only qualifier
 */
internal fun classIdFromQualifierParts(packageFqName: FqName, relativeClassFqName: FqName?): ClassId? =
    relativeClassFqName?.let { ClassId(packageFqName, it, isLocal = false) }

/**
 * Resolves the [ClassId] of a [FirResolvedQualifier] using only the stable members
 * `packageFqName` and `relativeClassFqName` (#90).
 *
 * **Deliberately NOT named `classId`.** `FirResolvedQualifier.classId` still exists as a member
 * on the pinned Kotlin 2.4.0, and Kotlin resolves a member before a same-named extension in the
 * same scope. An extension named `classId` here would therefore be silently shadowed at every
 * call site: the code would still compile and every 2.4.0 test would still pass, while the
 * `NoSuchMethodError` this change fixes would remain unfixed against Kotlin 2.4.20.
 *
 * This is the single sanctioned way to obtain a `ClassId` from a `FirResolvedQualifier` in this
 * module — see `NoDirectResolvedQualifierClassIdTest` for the structural guard that enforces it.
 */
internal fun FirResolvedQualifier.resolvedClassId(): ClassId? =
    classIdFromQualifierParts(packageFqName, relativeClassFqName)

/**
 * Regex matching a `if (<ident> is FirResolvedQualifier)` guard, capturing `<ident>`.
 */
private val RESOLVED_QUALIFIER_GUARD_REGEX =
    Regex("""if\s*\(\s*(\w+)\s+is\s+FirResolvedQualifier\s*\)""")

/**
 * Regex matching an inline `(... as FirResolvedQualifier).classId` / `as? ... )?.classId` cast.
 */
private val RESOLVED_QUALIFIER_INLINE_CAST_REGEX =
    Regex("""as\??\s+FirResolvedQualifier\s*\)?\??\.classId\b""")

/**
 * Pure detector core (#90): scans Kotlin [source] text for a direct `.classId` access on a
 * narrowed `FirResolvedQualifier` expression and returns the 1-indexed line number of each
 * violation found.
 *
 * Detects two shapes (R1, R2 from design):
 * - **R1**: `if (<ident> is FirResolvedQualifier) { ... <ident>.classId ... }` — captures
 *   `<ident>` from the guard, then flags `\b<ident>\.classId\b` on any later line.
 * - **R2**: an inline safe/unsafe cast immediately followed by `.classId`, e.g.
 *   `(e as? FirResolvedQualifier)?.classId`.
 *
 * Deliberately does NOT match `lookupTag.classId` / `lookupTag?.classId` (a different receiver —
 * the untouched `ConeClassLikeType` fallback chain) or `resolvedClassId()` (the sanctioned
 * helper). No allowlist: an allowlist would let the pattern back in.
 *
 * Comments are blanked out before scanning (preserving line numbers) so that KDoc/line comments
 * *documenting* these patterns — including this file's own KDoc — never register as violations.
 */
internal fun findDirectResolvedQualifierClassIdUsages(source: String): List<Int> {
    val violations = mutableListOf<Int>()
    var trackedIdentifier: String? = null

    blankOutComments(source).lines().forEachIndexed { index, line ->
        val lineNumber = index + 1

        RESOLVED_QUALIFIER_GUARD_REGEX.find(line)?.let { match ->
            trackedIdentifier = match.groupValues[1]
        }

        trackedIdentifier?.let { ident ->
            if (Regex("""\b${Regex.escape(ident)}\.classId\b""").containsMatchIn(line)) {
                violations += lineNumber
            }
        }

        if (RESOLVED_QUALIFIER_INLINE_CAST_REGEX.containsMatchIn(line)) {
            violations += lineNumber
        }
    }

    return violations
}

/**
 * Replaces the contents of `//` line comments, `/* ... */` block comments, and `"..."` string
 * literals with spaces (preserving every newline and overall length), so line-based scanning
 * never mistakes documentation text or string contents for real code.
 */
private fun blankOutComments(source: String): String {
    val result = StringBuilder(source.length)
    var index = 0
    var inLineComment = false
    var inBlockComment = false
    var inString = false

    while (index < source.length) {
        val current = source[index]
        val lookahead = source.getOrNull(index + 1)

        when {
            inLineComment -> {
                if (current == '\n') {
                    inLineComment = false
                    result.append(current)
                } else {
                    result.append(' ')
                }
            }
            inBlockComment -> {
                if (current == '*' && lookahead == '/') {
                    inBlockComment = false
                    result.append("  ")
                    index++
                } else if (current == '\n') {
                    result.append(current)
                } else {
                    result.append(' ')
                }
            }
            inString -> {
                if (current == '\\' && lookahead != null) {
                    result.append("  ")
                    index++
                } else if (current == '"') {
                    inString = false
                    result.append(' ')
                } else if (current == '\n') {
                    result.append(current)
                } else {
                    result.append(' ')
                }
            }
            current == '/' && lookahead == '/' -> {
                inLineComment = true
                result.append("  ")
                index++
            }
            current == '/' && lookahead == '*' -> {
                inBlockComment = true
                result.append("  ")
                index++
            }
            current == '"' -> {
                inString = true
                result.append(' ')
            }
            else -> result.append(current)
        }
        index++
    }

    return result.toString()
}
