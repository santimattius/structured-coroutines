/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object SharedMutableStateHeuristic {

    private val mutableFactoryNames = setOf(
        "mutableListOf",
        "mutableMapOf",
        "mutableSetOf",
        "arrayListOf",
        "hashMapOf",
    )

    fun findSharedMutableAccessIssues(block: KtBlockExpression): List<KtNameReferenceExpression> {
        val mutableVars = block.statements
            .filterIsInstance<KtProperty>()
            .filter { it.isVar && looksLikeMutableCollection(it) }
            .mapNotNull { it.name }
            .toSet()
        if (mutableVars.isEmpty()) return emptyList()

        val launchLambdas = mutableListOf<org.jetbrains.kotlin.psi.KtExpression>()
        block.accept(
            object : KtVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (expression.calleeExpression?.text == "launch") {
                        expression.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression?.let {
                            launchLambdas.add(it)
                        }
                    }
                    super.visitCallExpression(expression)
                }
            },
        )
        if (launchLambdas.size < 2) return emptyList()

        val issues = mutableListOf<KtNameReferenceExpression>()
        for (launchBody in launchLambdas) {
            launchBody.collectDescendantsOfType<KtNameReferenceExpression>()
                .filter { it.text in mutableVars && !isInsideMutexWithLock(it) }
                .forEach { issues.add(it) }
        }
        return issues.distinctBy { it.textOffset }
    }

    private fun looksLikeMutableCollection(property: KtProperty): Boolean {
        val typeText = property.typeReference?.text ?: ""
        val initText = property.initializer?.text ?: ""
        return typeText.contains("Mutable") ||
            mutableFactoryNames.any { initText.contains(it) }
    }

    private fun isInsideMutexWithLock(element: KtNameReferenceExpression): Boolean {
        var current = element.parent
        while (current != null) {
            val call = current as? KtCallExpression
            if (call?.calleeExpression?.text == "withLock") return true
            current = current.parent
        }
        return false
    }
}
