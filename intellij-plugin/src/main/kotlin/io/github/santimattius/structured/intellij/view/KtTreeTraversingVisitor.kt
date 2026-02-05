/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.view

import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Wraps a [KtVisitorVoid] (non-recursive) so that the entire PSI tree is traversed.
 * [KtVisitorVoid] only visits the node it is called on; [KtTreeVisitorVoid] recursively
 * visits children. This wrapper uses a tree visitor to traverse the file and delegates
 * each node to the inner visitor, so inspection callbacks (e.g. visitCallExpression)
 * are actually invoked for every matching element in the file.
 */
class KtTreeTraversingVisitor(private val delegate: KtVisitorVoid) : KtTreeVisitorVoid() {

    override fun visitKtFile(file: KtFile) {
        delegate.visitKtFile(file)
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        delegate.visitCallExpression(expression)
        super.visitCallExpression(expression)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        delegate.visitDotQualifiedExpression(expression)
        super.visitDotQualifiedExpression(expression)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        delegate.visitNamedFunction(function)
        super.visitNamedFunction(function)
    }

    override fun visitCatchSection(catchClause: KtCatchClause) {
        delegate.visitCatchSection(catchClause)
        super.visitCatchSection(catchClause)
    }
}
