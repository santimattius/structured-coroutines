/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.view.StructuredCoroutinesToolWindowFactory
import io.github.santimattius.structured.intellij.view.StructuredCoroutinesViewPanel

/**
 * AnAction that triggers a project-wide scan for all Structured Coroutines
 * inspection findings across every Kotlin source file.
 *
 * The action:
 * 1. Shows and activates the Structured Coroutines tool window.
 * 2. Delegates to [StructuredCoroutinesViewPanel.scanProject], which runs the
 *    scan on a background thread with a progress indicator.
 *
 * Accessible from:
 * - **Analyze** menu → "Scan Project for Coroutine Issues"
 * - The Structured Coroutines tool window toolbar (button)
 */
class ScanProjectAction : AnAction(
    StructuredCoroutinesBundle.message("action.scan.project.text"),
    StructuredCoroutinesBundle.message("action.scan.project.description"),
    AllIcons.Actions.Find
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Show the tool window so the user sees progress and results
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(StructuredCoroutinesToolWindowFactory.TOOL_WINDOW_ID) ?: return

        toolWindow.activate {
            val panel = StructuredCoroutinesToolWindowFactory.getPanel(project)
            panel?.scanProject()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
