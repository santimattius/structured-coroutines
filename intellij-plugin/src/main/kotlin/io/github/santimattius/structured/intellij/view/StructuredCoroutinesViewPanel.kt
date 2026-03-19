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

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtFile
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Tool window panel that lists Structured Coroutines inspection findings.
 *
 * Supports two scan modes:
 * - **Current file** — triggered by the [Refresh] button; scans only the active editor file.
 * - **Whole project** — triggered by the [Scan Project] button (or [ScanProjectAction]);
 *   runs a background scan across every Kotlin source file in the project.
 *
 * Layout:
 * - Toolbar: [Refresh] [Scan Project] [See guide (disabled until selection)]
 * - Table: Severity | Location | Inspection | What to do
 * - Detail bar: expanded "What to do" text + clickable "See guide →" link
 */
class StructuredCoroutinesViewPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val table: JBTable
    private val tableModel = object : DefaultTableModel(
        arrayOf(
            StructuredCoroutinesBundle.message("view.column.severity"),
            StructuredCoroutinesBundle.message("view.column.location"),
            StructuredCoroutinesBundle.message("view.column.inspection"),
            StructuredCoroutinesBundle.message("view.column.what.to.do")
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private var currentFindings: List<StructuredCoroutinesInspectionRunner.Finding> = emptyList()

    // Detail panel widgets
    private val seeGuideButton: JButton
    private val detailLabel: JBLabel

    init {
        // ─── Toolbar ─────────────────────────────────────────────────────────
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            border = JBUI.Borders.empty(0, 4)
        }

        val refreshButton = JButton(StructuredCoroutinesBundle.message("view.refresh")).apply {
            icon = AllIcons.Actions.Refresh
            toolTipText = StructuredCoroutinesBundle.message("view.refresh.tooltip")
            addActionListener { refresh() }
        }
        toolbar.add(refreshButton)

        val scanProjectButton = JButton(StructuredCoroutinesBundle.message("view.scan.project")).apply {
            icon = AllIcons.Actions.Find
            toolTipText = StructuredCoroutinesBundle.message("view.scan.project.tooltip")
            addActionListener { scanProject() }
        }
        toolbar.add(scanProjectButton)

        seeGuideButton = JButton(StructuredCoroutinesBundle.message("view.see.guide")).apply {
            icon = AllIcons.Ide.External_link_arrow
            isEnabled = false
            addActionListener { openGuideForSelected() }
        }
        toolbar.add(seeGuideButton)

        // ─── Table ───────────────────────────────────────────────────────────
        table = JBTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            rowSelectionAllowed = true
            setShowGrid(false)
            intercellSpacing = JBUI.emptySize()
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).apply {
                preferredWidth = 28
                maxWidth = 28
                cellRenderer = SeverityIconRenderer()
            }
            columnModel.getColumn(1).preferredWidth = 160
            columnModel.getColumn(2).preferredWidth = 180
            columnModel.getColumn(3).preferredWidth = 400

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) navigateToSelected()
                }
            })

            selectionModel.addListSelectionListener { onSelectionChanged() }
        }

        // ─── Detail bar ──────────────────────────────────────────────────────
        detailLabel = JBLabel("").apply {
            border = JBUI.Borders.empty(4, 8)
        }

        val detailPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            add(detailLabel, BorderLayout.CENTER)
        }

        // ─── Layout ──────────────────────────────────────────────────────────
        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(JBScrollPane(table), BorderLayout.CENTER)
        centerPanel.add(detailPanel, BorderLayout.SOUTH)

        add(toolbar, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)

        border = JBUI.Borders.empty(4)
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Scans the currently open file and populates the table. */
    fun refresh() {
        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: run {
                showStatusMessage(StructuredCoroutinesBundle.message("view.no.file.selected"))
                return
            }

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile !is KtFile) {
            showStatusMessage(StructuredCoroutinesBundle.message("view.not.kotlin.file"))
            return
        }

        val findings = StructuredCoroutinesInspectionRunner.runOnFile(project, psiFile)
        showFindings(findings, singleFile = psiFile)
    }

    /**
     * Scans every Kotlin source file in the project on a background thread,
     * then populates the table with aggregated results.
     *
     * Called by both the toolbar button and [ScanProjectAction].
     */
    fun scanProject() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            StructuredCoroutinesBundle.message("view.scan.project.progress"),
            /* canBeCancelled = */ true
        ) {
            private var findings: List<StructuredCoroutinesInspectionRunner.Finding> = emptyList()
            private var scannedFiles = 0

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = StructuredCoroutinesBundle.message("view.scan.project.progress")
                findings = StructuredCoroutinesInspectionRunner.runOnProject(project, indicator)
                scannedFiles = findings.map { it.descriptor.psiElement?.containingFile?.virtualFile?.path }
                    .distinct().size
            }

            override fun onSuccess() {
                val summary = StructuredCoroutinesBundle.message(
                    "view.scan.project.result",
                    findings.size,
                    scannedFiles
                )
                showFindings(findings, statusSuffix = summary)
            }

            override fun onCancel() {
                showStatusMessage(StructuredCoroutinesBundle.message("view.scan.project.cancelled"))
            }
        })
    }

    // ─── Display helpers ─────────────────────────────────────────────────────

    /**
     * Populates the table with the given [findings].
     *
     * @param singleFile If non-null, shows only the file name (Refresh mode).
     *                   If null, shows a relative project path (Scan Project mode).
     * @param statusSuffix Optional message appended to the detail label after populating.
     */
    private fun showFindings(
        findings: List<StructuredCoroutinesInspectionRunner.Finding>,
        singleFile: KtFile? = null,
        statusSuffix: String? = null
    ) {
        ApplicationManager.getApplication().invokeLater {
            currentFindings = findings
            tableModel.rowCount = 0
            clearDetail()

            if (findings.isEmpty()) {
                val msg = statusSuffix
                    ?: StructuredCoroutinesBundle.message("view.no.issues")
                tableModel.addRow(arrayOf("", "", "", msg))
                return@invokeLater
            }

            val projectBasePath = project.basePath

            for (finding in findings) {
                val element = finding.descriptor.psiElement ?: continue
                val containingFile = element.containingFile ?: continue
                val vf = containingFile.virtualFile ?: continue

                val line = if (singleFile != null) {
                    // Single-file mode: use the document from the passed-in file
                    val doc = PsiDocumentManager.getInstance(project).getDocument(singleFile)
                    val lineNum = doc?.getLineNumber(element.textOffset)?.plus(1) ?: 0
                    "${vf.name}:$lineNum"
                } else {
                    // Project-wide mode: show relative path for clarity
                    val doc = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                    val lineNum = doc?.getLineNumber(element.textOffset)?.plus(1) ?: 0
                    val relativePath = if (projectBasePath != null && vf.path.startsWith(projectBasePath)) {
                        vf.path.removePrefix("$projectBasePath/")
                    } else {
                        vf.name
                    }
                    "$relativePath:$lineNum"
                }

                tableModel.addRow(
                    arrayOf(finding.severity, line, finding.inspectionName, finding.whatToDo)
                )
            }

            if (statusSuffix != null) {
                detailLabel.text = "<html><i>$statusSuffix</i></html>"
            }
        }
    }

    private fun showStatusMessage(message: String) {
        currentFindings = emptyList()
        tableModel.rowCount = 0
        tableModel.addRow(arrayOf("", "", "", message))
        clearDetail()
    }

    // ─── Selection handling ───────────────────────────────────────────────────

    private fun onSelectionChanged() {
        val row = table.selectedRow
        if (row < 0 || row >= currentFindings.size) {
            clearDetail()
            return
        }
        val finding = currentFindings[row]
        seeGuideButton.isEnabled = finding.guideUrl.isNotEmpty()
        updateDetailLabel(finding)
    }

    private fun updateDetailLabel(finding: StructuredCoroutinesInspectionRunner.Finding) {
        if (finding.whatToDo.isEmpty()) {
            detailLabel.text = ""
        } else {
            val guideHtml = if (finding.guideUrl.isNotEmpty()) {
                "&nbsp;&nbsp;<a href=\"${finding.guideUrl}\">${StructuredCoroutinesBundle.message("view.see.guide")} →</a>"
            } else ""
            detailLabel.text =
                "<html><b>${StructuredCoroutinesBundle.message("view.detail.what.to.do")}:</b> ${finding.whatToDo}$guideHtml</html>"
        }
    }

    private fun clearDetail() {
        seeGuideButton.isEnabled = false
        detailLabel.text = ""
    }

    private fun openGuideForSelected() {
        val row = table.selectedRow
        if (row < 0 || row >= currentFindings.size) return
        val url = currentFindings[row].guideUrl
        if (url.isNotEmpty()) BrowserUtil.browse(url)
    }

    private fun navigateToSelected() {
        val row = table.selectedRow
        if (row < 0 || row >= currentFindings.size) return
        val finding = currentFindings[row]
        val element = finding.descriptor.psiElement ?: return
        val vf = element.containingFile?.virtualFile ?: return
        OpenFileDescriptor(project, vf, element.textOffset).navigate(true)
    }

    // ─── Cell renderers ───────────────────────────────────────────────────────

    private inner class SeverityIconRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            text = ""
            icon = when (value?.toString()) {
                "ERROR" -> AllIcons.General.Error
                "WARNING" -> AllIcons.General.Warning
                else -> null
            }
            return c
        }
    }
}
