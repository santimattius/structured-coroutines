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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtFile
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Tool window panel that lists all Structured Coroutines inspection findings
 * for the current file. Refresh runs inspections and double-click navigates to the issue.
 */
class StructuredCoroutinesViewPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val table: JBTable
    private val tableModel = object : DefaultTableModel(
        arrayOf(
            StructuredCoroutinesBundle.message("view.column.severity"),
            StructuredCoroutinesBundle.message("view.column.location"),
            StructuredCoroutinesBundle.message("view.column.inspection"),
            StructuredCoroutinesBundle.message("view.column.message")
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private var currentFindings: List<StructuredCoroutinesInspectionRunner.Finding> = emptyList()

    init {
        val toolbar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 4)
        }
        val refreshButton = JButton(StructuredCoroutinesBundle.message("view.refresh")).apply {
            addActionListener { refresh() }
            icon = AllIcons.Actions.Refresh
        }
        toolbar.add(refreshButton, BorderLayout.WEST)

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
            columnModel.getColumn(3).preferredWidth = 400
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        navigateToSelected()
                    }
                }
            })
        }

        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)

        border = JBUI.Borders.empty(8)
    }

    fun refresh() {
        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: run {
                tableModel.rowCount = 0
                tableModel.addRow(arrayOf("", "", "", StructuredCoroutinesBundle.message("view.no.file.selected")))
                return
            }

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile !is KtFile) {
            tableModel.rowCount = 0
            tableModel.addRow(arrayOf("", "", "", StructuredCoroutinesBundle.message("view.not.kotlin.file")))
            return
        }

        runFindings(psiFile)
    }

    private fun runFindings(file: KtFile) {
        currentFindings = StructuredCoroutinesInspectionRunner.runOnFile(project, file)
        tableModel.rowCount = 0
        if (currentFindings.isEmpty()) {
            tableModel.addRow(arrayOf("", "", "", StructuredCoroutinesBundle.message("view.no.issues")))
            return
        }
        val doc = PsiDocumentManager.getInstance(project).getDocument(file)
        val vf = file.virtualFile ?: return
        for (finding in currentFindings) {
            val element = finding.descriptor.psiElement ?: continue
            val line = doc?.getLineNumber(element.textOffset)?.plus(1) ?: 0
            val location = "${vf.name}:$line"
            val message = finding.descriptor.descriptionTemplate
            tableModel.addRow(arrayOf(finding.severity, location, finding.inspectionName, message))
        }
    }

    private fun navigateToSelected() {
        val row = table.selectedRow
        if (row < 0 || row >= currentFindings.size) return
        val finding = currentFindings[row]
        val element = finding.descriptor.psiElement ?: return
        val vf = element.containingFile?.virtualFile ?: return
        OpenFileDescriptor(project, vf, element.textOffset).navigate(true)
    }

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
            when (value?.toString()) {
                "ERROR" -> icon = AllIcons.General.Error
                "WARNING" -> icon = AllIcons.General.Warning
                else -> icon = null
            }
            return c
        }
    }
}
