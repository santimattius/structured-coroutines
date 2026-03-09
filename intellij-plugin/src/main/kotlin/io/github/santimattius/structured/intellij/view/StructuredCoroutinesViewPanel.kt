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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
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
 * Tool window panel that lists all Structured Coroutines inspection findings
 * for the current file. Each finding shows a short "What to do" action summary;
 * selecting a row enables the "See guide" link that opens the corresponding
 * section in BEST_PRACTICES_COROUTINES.md.
 *
 * Layout:
 * - Toolbar: [Refresh] [See guide (disabled until selection)]
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
        // — Toolbar —
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            border = JBUI.Borders.empty(0, 4)
        }

        val refreshButton = JButton(StructuredCoroutinesBundle.message("view.refresh")).apply {
            addActionListener { refresh() }
            icon = AllIcons.Actions.Refresh
        }
        toolbar.add(refreshButton)

        seeGuideButton = JButton(StructuredCoroutinesBundle.message("view.see.guide")).apply {
            icon = AllIcons.Ide.External_link_arrow
            isEnabled = false
            addActionListener { openGuideForSelected() }
        }
        toolbar.add(seeGuideButton)

        // — Table —
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
            columnModel.getColumn(1).preferredWidth = 120
            columnModel.getColumn(2).preferredWidth = 180
            columnModel.getColumn(3).preferredWidth = 440

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) navigateToSelected()
                }
            })

            selectionModel.addListSelectionListener {
                onSelectionChanged()
            }
        }

        // — Detail bar at the bottom —
        // JBLabel inherits the correct foreground color from the active theme by default.
        detailLabel = JBLabel("").apply {
            border = JBUI.Borders.empty(4, 8)
        }

        val detailPanel = JPanel(BorderLayout()).apply {
            // JBColor.border() is a stable, theme-aware color for divider lines (available since 2020.x).
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            add(detailLabel, BorderLayout.CENTER)
        }

        // — Layout via JSplitPane —
        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(JBScrollPane(table), BorderLayout.CENTER)
        centerPanel.add(detailPanel, BorderLayout.SOUTH)

        add(toolbar, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)

        border = JBUI.Borders.empty(4)
    }

    fun refresh() {
        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: run {
                tableModel.rowCount = 0
                tableModel.addRow(
                    arrayOf("", "", "", StructuredCoroutinesBundle.message("view.no.file.selected"))
                )
                clearDetail()
                return
            }

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile !is KtFile) {
            tableModel.rowCount = 0
            tableModel.addRow(
                arrayOf("", "", "", StructuredCoroutinesBundle.message("view.not.kotlin.file"))
            )
            clearDetail()
            return
        }

        runFindings(psiFile)
    }

    private fun runFindings(file: KtFile) {
        currentFindings = StructuredCoroutinesInspectionRunner.runOnFile(project, file)
        tableModel.rowCount = 0
        clearDetail()

        if (currentFindings.isEmpty()) {
            tableModel.addRow(
                arrayOf("", "", "", StructuredCoroutinesBundle.message("view.no.issues"))
            )
            return
        }

        val doc = PsiDocumentManager.getInstance(project).getDocument(file)
        val vf = file.virtualFile ?: return
        for (finding in currentFindings) {
            val element = finding.descriptor.psiElement ?: continue
            val line = doc?.getLineNumber(element.textOffset)?.plus(1) ?: 0
            val location = "${vf.name}:$line"
            tableModel.addRow(
                arrayOf(finding.severity, location, finding.inspectionName, finding.whatToDo)
            )
        }
    }

    // — Selection handling —

    private fun onSelectionChanged() {
        val row = table.selectedRow
        if (row < 0 || row >= currentFindings.size) {
            clearDetail()
            return
        }
        val finding = currentFindings[row]
        val hasGuide = finding.guideUrl.isNotEmpty()
        seeGuideButton.isEnabled = hasGuide
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

    // — Cell renderers —

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
