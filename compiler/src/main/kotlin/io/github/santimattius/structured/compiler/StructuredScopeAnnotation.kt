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

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val STRUCTURED_SCOPE_ANNOTATION_CLASS_ID = ClassId(
    FqName("io.github.santimattius.structured.annotations"),
    Name.identifier("StructuredScope"),
)

@OptIn(SymbolInternals::class)
internal fun FirValueParameterSymbol.hasStructuredScope(session: FirSession): Boolean =
    hasStructuredScope(session, hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, session), fir.annotations)

@OptIn(SymbolInternals::class)
internal fun FirPropertySymbol.hasStructuredScope(session: FirSession): Boolean =
    hasStructuredScope(session, hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, session), fir.annotations)

/**
 * Returns true when [hasDirect] is true, or any [annotations] type is meta-marked with @StructuredScope.
 */
private fun hasStructuredScope(
    session: FirSession,
    hasDirect: Boolean,
    annotations: List<FirAnnotation>,
): Boolean {
    if (hasDirect) return true
    return annotations.any { it.isMetaStructuredScope(session) }
}

private fun FirAnnotation.isMetaStructuredScope(session: FirSession): Boolean {
    val annotationClass = resolveAnnotationClassSymbol(session) ?: return false
    return annotationClass.hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, session)
}

private fun FirAnnotation.resolveAnnotationClassSymbol(session: FirSession): FirRegularClassSymbol? {
    toAnnotationClassLikeSymbol(session)?.let { symbol ->
        return symbol as? FirRegularClassSymbol
    }
    val classId = toAnnotationClassId(session) ?: toAnnotationClassIdSafe(session) ?: return null
    return session.symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol
}
