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

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-core unit tests for [classIdFromQualifierParts] (#90).
 *
 * `FirResolvedQualifier.classId` was removed as a member in Kotlin 2.4.20 (KT-84522);
 * [classIdFromQualifierParts] reconstructs the same [ClassId] from the stable
 * `packageFqName`/`relativeClassFqName` parts, without needing a real `FirResolvedQualifier`
 * instance — this codebase has no precedent for constructing FIR nodes in unit tests.
 */
class FirQualifierResolutionTest {

    @Test
    fun `top-level relative name resolves to NonCancellable ClassId`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName("kotlinx.coroutines"),
            relativeClassFqName = FqName("NonCancellable"),
        )

        assertEquals(
            ClassId(FqName("kotlinx.coroutines"), Name.identifier("NonCancellable")),
            result,
        )
    }

    @Test
    fun `top-level relative name resolves to GlobalScope ClassId`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName("kotlinx.coroutines"),
            relativeClassFqName = FqName("GlobalScope"),
        )

        assertEquals(
            ClassId(FqName("kotlinx.coroutines"), Name.identifier("GlobalScope")),
            result,
        )
    }

    @Test
    fun `top-level relative name resolves to Dispatchers ClassId`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName("kotlinx.coroutines"),
            relativeClassFqName = FqName("Dispatchers"),
        )

        assertEquals(
            ClassId(FqName("kotlinx.coroutines"), Name.identifier("Dispatchers")),
            result,
        )
    }

    @Test
    fun `null relativeClassFqName returns null`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName("kotlinx.coroutines"),
            relativeClassFqName = null,
        )

        assertNull(result)
    }

    @Test
    fun `nested class fqName resolves to a nested ClassId`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName("kotlinx.coroutines"),
            relativeClassFqName = FqName("Outer.Inner"),
        )

        assertNotNull(result)
        assertTrue(result.isNestedClass)
        assertEquals(FqName("kotlinx.coroutines.Outer.Inner"), result.asSingleFqName())
    }

    @Test
    fun `root package qualifier resolves without a package prefix`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName.ROOT,
            relativeClassFqName = FqName("TopLevelClass"),
        )

        assertEquals(
            ClassId(FqName.ROOT, Name.identifier("TopLevelClass")),
            result,
        )
    }

    @Test
    fun `reconstructed ClassId is never local`() {
        val result = classIdFromQualifierParts(
            packageFqName = FqName("kotlinx.coroutines"),
            relativeClassFqName = FqName("NonCancellable"),
        )

        assertNotNull(result)
        assertFalse(result.isLocal)
    }
}
