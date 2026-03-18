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

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.util.WeakHashMap

/**
 * Creates the "Structured Coroutines" tool window content.
 *
 * Holds a [WeakHashMap] of project → panel so that [ScanProjectAction]
 * can trigger a project-wide scan from outside the tool window hierarchy
 * without retaining a strong reference that would prevent GC.
 */
class StructuredCoroutinesToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOL_WINDOW_ID = "StructuredCoroutines"

        private val panels: WeakHashMap<Project, StructuredCoroutinesViewPanel> = WeakHashMap()

        /** Returns the panel for the given [project], or null if the tool window was never opened. */
        fun getPanel(project: Project): StructuredCoroutinesViewPanel? = panels[project]
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = StructuredCoroutinesViewPanel(project)
        panels[project] = panel
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
