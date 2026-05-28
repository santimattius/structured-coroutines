/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

object MainScopeCleanupHeuristic {

    val cleanupMethodNames = setOf("onDestroy", "onCleared", "dispose", "close")

    data class MainScopeIssue(val property: KtProperty, val reason: String)

    fun findIssues(klass: KtClass): List<MainScopeIssue> {
        val mainScopeProps = klass.body?.properties?.filter { property ->
            val init = property.initializer?.text ?: ""
            val typeText = property.typeReference?.text ?: ""
            init.contains("MainScope()") || typeText.contains("MainScope")
        } ?: emptyList()
        if (mainScopeProps.isEmpty()) return emptyList()

        val cleanupFns = klass.declarations.filterIsInstance<KtNamedFunction>()
            .filter { it.name in cleanupMethodNames }

        return mainScopeProps.map { prop ->
            val scopeName = prop.name ?: "scope"
            val hasCancel = cleanupFns.any { fn ->
                val body = fn.bodyExpression?.text ?: ""
                body.contains("$scopeName.cancel()") ||
                    (body.contains("cancel()") && body.contains(scopeName))
            }
            if (cleanupFns.isEmpty()) {
                MainScopeIssue(prop, "no cleanup method (${cleanupMethodNames.joinToString()})")
            } else if (!hasCancel) {
                MainScopeIssue(prop, "cleanup exists but scope.cancel() missing")
            } else {
                null
            }
        }.filterNotNull()
    }
}
