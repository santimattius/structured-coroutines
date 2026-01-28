/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.guttericons

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import io.github.santimattius.structured.intellij.utils.ScopeAnalyzer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Line marker provider that shows gutter icons for coroutine scope types.
 *
 * Displays different icons/colors for:
 * - viewModelScope (green)
 * - lifecycleScope (blue)
 * - GlobalScope (red - warning)
 * - coroutineScope/supervisorScope (purple)
 * - Custom scopes (gray)
 */
class CoroutineScopeLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process call expressions
        val callExpression = element as? KtCallExpression ?: return null

        // Check if this is a coroutine builder call
        val calleeName = callExpression.calleeExpression?.text ?: return null
        if (calleeName !in setOf("launch", "async")) return null

        // Get the scope information
        val scopeInfo = getScopeInfo(callExpression) ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            scopeInfo.icon,
            { scopeInfo.tooltip },
            null,
            GutterIconRenderer.Alignment.CENTER
        ) { scopeInfo.tooltip }
    }

    private fun getScopeInfo(call: KtCallExpression): ScopeInfo? {
        val scopeName = CoroutinePsiUtils.getScopeName(call)

        // Check for framework scopes
        if (CoroutinePsiUtils.isFrameworkScopeCall(call)) {
            return when (scopeName) {
                "viewModelScope" -> ScopeInfo(
                    createScopeIcon(VIEWMODEL_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.viewmodel")
                )
                "lifecycleScope" -> ScopeInfo(
                    createScopeIcon(LIFECYCLE_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.lifecycle")
                )
                else -> ScopeInfo(
                    createScopeIcon(CUSTOM_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.custom")
                )
            }
        }

        // Check for GlobalScope
        if (CoroutinePsiUtils.isGlobalScopeCall(call)) {
            return ScopeInfo(
                createScopeIcon(GLOBAL_COLOR),
                StructuredCoroutinesBundle.message("gutter.scope.global")
            )
        }

        // Check for inline CoroutineScope
        if (CoroutinePsiUtils.isInlineCoroutineScopeCreation(call)) {
            return ScopeInfo(
                createScopeIcon(GLOBAL_COLOR),
                StructuredCoroutinesBundle.message("gutter.scope.custom")
            )
        }

        // Check if inside a coroutineScope/supervisorScope builder
        val parent = call.parent
        if (parent is KtCallExpression || isInsideStructuredBuilder(call)) {
            return ScopeInfo(
                createScopeIcon(STRUCTURED_COLOR),
                StructuredCoroutinesBundle.message("gutter.scope.coroutine")
            )
        }

        // Check for named scope
        if (scopeName != null) {
            val scopeType = ScopeAnalyzer.getScopeType(scopeName, call)
            return when (scopeType) {
                ScopeAnalyzer.ScopeType.VIEW_MODEL -> ScopeInfo(
                    createScopeIcon(VIEWMODEL_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.viewmodel")
                )
                ScopeAnalyzer.ScopeType.LIFECYCLE -> ScopeInfo(
                    createScopeIcon(LIFECYCLE_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.lifecycle")
                )
                ScopeAnalyzer.ScopeType.GLOBAL -> ScopeInfo(
                    createScopeIcon(GLOBAL_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.global")
                )
                else -> ScopeInfo(
                    createScopeIcon(CUSTOM_COLOR),
                    StructuredCoroutinesBundle.message("gutter.scope.custom")
                )
            }
        }

        return null
    }

    private fun isInsideStructuredBuilder(call: KtCallExpression): Boolean {
        var current: PsiElement? = call.parent
        while (current != null) {
            if (current is KtCallExpression) {
                val callee = current.calleeExpression?.text
                if (callee == "coroutineScope" || callee == "supervisorScope") {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun createScopeIcon(color: Color): Icon {
        return object : Icon {
            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2d = g.create() as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = color
                g2d.fillOval(x + 2, y + 2, iconWidth - 4, iconHeight - 4)
                g2d.color = color.darker()
                g2d.drawOval(x + 2, y + 2, iconWidth - 4, iconHeight - 4)
                g2d.dispose()
            }

            override fun getIconWidth(): Int = 12
            override fun getIconHeight(): Int = 12
        }
    }

    private data class ScopeInfo(val icon: Icon, val tooltip: String)

    companion object {
        private val VIEWMODEL_COLOR = JBColor(Color(76, 175, 80), Color(76, 175, 80))  // Green
        private val LIFECYCLE_COLOR = JBColor(Color(33, 150, 243), Color(33, 150, 243))  // Blue
        private val GLOBAL_COLOR = JBColor(Color(244, 67, 54), Color(244, 67, 54))  // Red
        private val STRUCTURED_COLOR = JBColor(Color(156, 39, 176), Color(156, 39, 176))  // Purple
        private val CUSTOM_COLOR = JBColor(Color(158, 158, 158), Color(158, 158, 158))  // Gray
    }
}
