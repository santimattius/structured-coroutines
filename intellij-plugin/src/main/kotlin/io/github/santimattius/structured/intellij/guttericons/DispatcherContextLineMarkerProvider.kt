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
import io.github.santimattius.structured.intellij.utils.ScopeAnalyzer
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Line marker provider that shows gutter icons for dispatcher context.
 *
 * Displays different icons/colors for:
 * - Dispatchers.Main (orange - UI thread)
 * - Dispatchers.IO (blue - I/O operations)
 * - Dispatchers.Default (green - CPU-intensive)
 * - Dispatchers.Unconfined (red - warning)
 */
class DispatcherContextLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process withContext calls
        val callExpression = element as? KtCallExpression ?: return null
        val calleeName = callExpression.calleeExpression?.text ?: return null

        // Only show for withContext and coroutine builders with explicit dispatcher
        if (calleeName !in setOf("withContext", "launch", "async")) return null

        // Get the dispatcher type
        val dispatcherType = ScopeAnalyzer.getDispatcherType(callExpression)
        if (dispatcherType == ScopeAnalyzer.DispatcherType.INHERITED) return null

        val dispatcherInfo = getDispatcherInfo(dispatcherType) ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            dispatcherInfo.icon,
            { dispatcherInfo.tooltip },
            null,
            GutterIconRenderer.Alignment.RIGHT
        ) { dispatcherInfo.tooltip }
    }

    private fun getDispatcherInfo(type: ScopeAnalyzer.DispatcherType): DispatcherInfo? {
        return when (type) {
            ScopeAnalyzer.DispatcherType.MAIN -> DispatcherInfo(
                createDispatcherIcon(MAIN_COLOR, "M"),
                StructuredCoroutinesBundle.message("gutter.dispatcher.main")
            )
            ScopeAnalyzer.DispatcherType.IO -> DispatcherInfo(
                createDispatcherIcon(IO_COLOR, "I"),
                StructuredCoroutinesBundle.message("gutter.dispatcher.io")
            )
            ScopeAnalyzer.DispatcherType.DEFAULT -> DispatcherInfo(
                createDispatcherIcon(DEFAULT_COLOR, "D"),
                StructuredCoroutinesBundle.message("gutter.dispatcher.default")
            )
            ScopeAnalyzer.DispatcherType.UNCONFINED -> DispatcherInfo(
                createDispatcherIcon(UNCONFINED_COLOR, "U"),
                StructuredCoroutinesBundle.message("gutter.dispatcher.unconfined")
            )
            ScopeAnalyzer.DispatcherType.INHERITED -> null
        }
    }

    private fun createDispatcherIcon(color: Color, letter: String): Icon {
        return object : Icon {
            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2d = g.create() as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                // Draw rounded rectangle background
                g2d.color = color
                g2d.fillRoundRect(x, y + 1, iconWidth, iconHeight - 2, 4, 4)

                // Draw letter
                g2d.color = Color.WHITE
                g2d.font = g2d.font.deriveFont(8f)
                val fm = g2d.fontMetrics
                val textX = x + (iconWidth - fm.stringWidth(letter)) / 2
                val textY = y + (iconHeight + fm.ascent - fm.descent) / 2
                g2d.drawString(letter, textX, textY)

                g2d.dispose()
            }

            override fun getIconWidth(): Int = 12
            override fun getIconHeight(): Int = 12
        }
    }

    private data class DispatcherInfo(val icon: Icon, val tooltip: String)

    companion object {
        private val MAIN_COLOR = JBColor(Color(255, 152, 0), Color(255, 152, 0))  // Orange
        private val IO_COLOR = JBColor(Color(33, 150, 243), Color(33, 150, 243))  // Blue
        private val DEFAULT_COLOR = JBColor(Color(76, 175, 80), Color(76, 175, 80))  // Green
        private val UNCONFINED_COLOR = JBColor(Color(244, 67, 54), Color(244, 67, 54))  // Red
    }
}
