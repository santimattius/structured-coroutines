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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural guard (#90, precedent: [NoDeadSeverityParameterOverloadsTest]) against a direct
 * `.classId` call on a `FirResolvedQualifier` ever reappearing under `compiler/src/main`.
 *
 * `FirResolvedQualifier.classId` was removed as a member in Kotlin 2.4.20 (KT-84522); every
 * checker must resolve it through the shared `FirResolvedQualifier.resolvedClassId()` helper in
 * `FirQualifierResolution.kt` instead. [findDirectResolvedQualifierClassIdUsages] is the pure
 * detector core; the whole-tree scan test below is its filesystem-walk wrapper.
 */
class NoDirectResolvedQualifierClassIdTest {

    @Test
    fun `detects direct classId access inside an is-check guard`() {
        val source = """
            fun check(x: FirExpression) {
                if (x is FirResolvedQualifier) {
                    return x.classId == TARGET
                }
            }
        """.trimIndent()

        val violations = findDirectResolvedQualifierClassIdUsages(source)

        assertEquals(1, violations.size)
    }

    @Test
    fun `detects direct classId access inline through a safe cast`() {
        val source = """
            fun check(e: FirExpression) {
                val id = (e as? FirResolvedQualifier)?.classId
            }
        """.trimIndent()

        val violations = findDirectResolvedQualifierClassIdUsages(source)

        assertEquals(1, violations.size)
    }

    @Test
    fun `does not flag lookupTag classId access`() {
        val source = """
            fun check(type: ConeClassLikeType) {
                val id = type.lookupTag.classId
            }
        """.trimIndent()

        assertTrue(findDirectResolvedQualifierClassIdUsages(source).isEmpty())
    }

    @Test
    fun `does not flag nullable lookupTag classId access`() {
        val source = """
            fun check(type: ConeClassLikeType?) {
                val id = type?.lookupTag?.classId
            }
        """.trimIndent()

        assertTrue(findDirectResolvedQualifierClassIdUsages(source).isEmpty())
    }

    @Test
    fun `does not flag the sanctioned resolvedClassId helper`() {
        val source = """
            fun check(x: FirResolvedQualifier) {
                val id = x.resolvedClassId()
            }
        """.trimIndent()

        assertTrue(findDirectResolvedQualifierClassIdUsages(source).isEmpty())
    }

    @Test
    fun `no file under compiler src main directly accesses FirResolvedQualifier classId`() {
        val rootDir = System.getProperty("structuredCoroutines.rootDir")
            ?: error("structuredCoroutines.rootDir system property not set — run via Gradle (:compiler:test)")
        val mainSourceDir = File(rootDir, "compiler/src/main/kotlin")

        val offendingSites = mainSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                findDirectResolvedQualifierClassIdUsages(file.readText())
                    .map { line -> "${file.name}:$line" }
            }
            .toList()

        assertTrue(
            offendingSites.isEmpty(),
            "Found direct FirResolvedQualifier.classId access outside the shared helper: $offendingSites",
        )
    }
}
