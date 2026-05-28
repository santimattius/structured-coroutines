/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

object SideEffectInMapHeuristic {

    private val sideEffectCalleeHints = setOf(
        "track",
        "log",
        "println",
        "print",
        "debug",
        "info",
        "warn",
        "error",
        "insert",
        "update",
        "delete",
        "execute",
        "emit",
    )

    fun hasSideEffectBeforeReturn(lambda: KtLambdaExpression): Boolean {
        val body = lambda.bodyExpression ?: return false
        val statements = body.statements
        if (statements.size < 2) return false
        val last = statements.last()
        return statements.dropLast(1).any { stmt ->
            var found = false
            stmt.accept(
                object : KtVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        val callee = expression.calleeExpression?.text ?: ""
                        val selector = (expression.parent as? KtDotQualifiedExpression)
                            ?.selectorExpression?.text
                        if (callee in sideEffectCalleeHints || selector in sideEffectCalleeHints) {
                            found = true
                        }
                        super.visitCallExpression(expression)
                    }
                },
            )
            found
        }
    }
}
