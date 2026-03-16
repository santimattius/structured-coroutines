/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates a report of the Structured Coroutines plugin configuration.
 *
 * The report shows each compiler rule, its configured severity (error/warning),
 * the rule code, the best-practice section, and a direct link to the documentation.
 * This is useful for CI pipelines to audit which rules are active and at what severity.
 *
 * ## Usage
 *
 * ```bash
 * ./gradlew structuredCoroutinesReport
 * ```
 *
 * Reports are written to `build/reports/structured-coroutines/` by default.
 * Configure the output directory or format in the `structuredCoroutines {}` block:
 *
 * ```kotlin
 * structuredCoroutines {
 *     reportOutputDir.set(layout.buildDirectory.dir("my-reports/coroutines"))
 *     reportFormat.set("html")  // "html", "text", or "all" (default)
 * }
 * ```
 *
 * ## Output files
 *
 * - `structured-coroutines-report.html` — visual report for browsers and CI artifact viewers
 * - `structured-coroutines-report.txt`  — plain-text summary for CI logs
 */
@CacheableTask
abstract class StructuredCoroutinesReportTask : DefaultTask() {

    // ─── Rule metadata ──────────────────────────────────────────────────────

    private data class RuleInfo(
        val propertyKey: String,
        val ruleCode: String,
        val section: String,
        val title: String,
        val docAnchor: String,
    )

    private val rules = listOf(
        RuleInfo("globalScopeUsage",              "SCOPE_001",    "1.1", "Using GlobalScope in Production Code",               "11-using-globalscope-in-production-code"),
        RuleInfo("unusedDeferred",                "SCOPE_002",    "1.2", "Using async Without Calling await",                  "12-scope_002--using-async-without-calling-await"),
        RuleInfo("unstructuredLaunch",            "SCOPE_003",    "1.3", "Breaking Structured Concurrency (launch)",           "13-scope_003--breaking-structured-concurrency"),
        RuleInfo("inlineCoroutineScope",          "SCOPE_003",    "1.3", "Breaking Structured Concurrency (inline scope)",     "13-scope_003--breaking-structured-concurrency"),
        RuleInfo("redundantLaunchInCoroutineScope","RUNBLOCK_001","2.1", "Using launch on Last Line of coroutineScope",        "21-runblock_001--using-launch-on-the-last-line-of-coroutinescope"),
        RuleInfo("runBlockingInSuspend",          "RUNBLOCK_002", "2.2", "Using runBlocking Inside Suspend Functions",         "22-runblock_002--using-runblocking-inside-suspend-functions"),
        RuleInfo("dispatchersUnconfined",         "DISPATCH_003", "3.3", "Abusing Dispatchers.Unconfined",                    "33-dispatch_003--abusing-dispatchersunconfined"),
        RuleInfo("jobInBuilderContext",           "DISPATCH_004", "3.4", "Passing Job() Directly as Context to Builders",      "34-dispatch_004--passing-job-directly-as-context-to-builders"),
        RuleInfo("loopWithoutYield",              "CANCEL_001",   "4.1", "Ignoring Cancellation in Intensive Loops",           "41-cancel_001--ignoring-cancellation-in-intensive-loops"),
        RuleInfo("cancellationExceptionSwallowed","CANCEL_003",   "4.3", "Swallowing CancellationException",                  "43-cancel_003--swallowing-cancellationexception"),
        RuleInfo("suspendInFinally",              "CANCEL_004",   "4.4", "Suspendable Cleanup Without NonCancellable",         "44-cancel_004--suspendable-cleanup-without-noncancellable"),
        RuleInfo("cancellationExceptionSubclass", "EXCEPT_002",   "5.2", "Extending CancellationException for Domain Errors",  "52-except_002--extending-cancellationexception-for-domain-errors"),
    )

    private val docBase =
        "https://github.com/santimattius/structured-coroutines/blob/main/docs/BEST_PRACTICES_COROUTINES.md"

    // ─── Inputs ─────────────────────────────────────────────────────────────

    @get:Input abstract val projectName: Property<String>
    @get:Input abstract val pluginVersion: Property<String>

    // Rule severities
    @get:Input abstract val globalScopeUsage: Property<String>
    @get:Input abstract val inlineCoroutineScope: Property<String>
    @get:Input abstract val unstructuredLaunch: Property<String>
    @get:Input abstract val runBlockingInSuspend: Property<String>
    @get:Input abstract val jobInBuilderContext: Property<String>
    @get:Input abstract val dispatchersUnconfined: Property<String>
    @get:Input abstract val cancellationExceptionSubclass: Property<String>
    @get:Input abstract val suspendInFinally: Property<String>
    @get:Input abstract val cancellationExceptionSwallowed: Property<String>
    @get:Input abstract val unusedDeferred: Property<String>
    @get:Input abstract val redundantLaunchInCoroutineScope: Property<String>
    @get:Input abstract val loopWithoutYield: Property<String>

    // Exclusions
    @get:Input abstract val excludedSourceSets: ListProperty<String>
    @get:Input abstract val excludedProjects: ListProperty<String>

    /** Report format: `"html"`, `"text"`, or `"all"` (default). */
    @get:Input abstract val reportFormat: Property<String>

    // ─── Output ─────────────────────────────────────────────────────────────

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    // ─── Action ─────────────────────────────────────────────────────────────

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile
        dir.mkdirs()

        val severityMap = mapOf(
            "globalScopeUsage"               to globalScopeUsage.get(),
            "inlineCoroutineScope"           to inlineCoroutineScope.get(),
            "unstructuredLaunch"             to unstructuredLaunch.get(),
            "runBlockingInSuspend"           to runBlockingInSuspend.get(),
            "jobInBuilderContext"            to jobInBuilderContext.get(),
            "dispatchersUnconfined"          to dispatchersUnconfined.get(),
            "cancellationExceptionSubclass"  to cancellationExceptionSubclass.get(),
            "suspendInFinally"               to suspendInFinally.get(),
            "cancellationExceptionSwallowed" to cancellationExceptionSwallowed.get(),
            "unusedDeferred"                 to unusedDeferred.get(),
            "redundantLaunchInCoroutineScope" to redundantLaunchInCoroutineScope.get(),
            "loopWithoutYield"               to loopWithoutYield.get(),
        )

        val format    = reportFormat.getOrElse("all")
        val project   = projectName.getOrElse("(unknown)")
        val version   = pluginVersion.getOrElse("unknown")
        val excluded  = excludedSourceSets.getOrElse(emptyList())
        val excProjs  = excludedProjects.getOrElse(emptyList())
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        if (format == "html" || format == "all") {
            val file = dir.resolve("structured-coroutines-report.html")
            file.writeText(generateHtml(project, version, timestamp, severityMap, excluded, excProjs))
            logger.lifecycle("Structured Coroutines report (HTML): ${file.absolutePath}")
        }

        if (format == "text" || format == "all") {
            val file = dir.resolve("structured-coroutines-report.txt")
            file.writeText(generateText(project, version, timestamp, severityMap, excluded, excProjs))
            logger.lifecycle("Structured Coroutines report (text): ${file.absolutePath}")
        }
    }

    // ─── Text generator ─────────────────────────────────────────────────────

    private fun generateText(
        project: String,
        version: String,
        timestamp: String,
        severityMap: Map<String, String>,
        excludedSourceSets: List<String>,
        excludedProjects: List<String>,
    ): String {
        val sep = "─".repeat(72)
        val sb = StringBuilder()

        sb.appendLine("═".repeat(72))
        sb.appendLine("  Structured Coroutines — Plugin Configuration Report")
        sb.appendLine("═".repeat(72))
        sb.appendLine("  Project  : $project")
        sb.appendLine("  Version  : $version")
        sb.appendLine("  Generated: $timestamp")
        sb.appendLine()

        if (excludedSourceSets.isNotEmpty()) {
            sb.appendLine("  Excluded source sets : ${excludedSourceSets.joinToString(", ")}")
        }
        if (excludedProjects.isNotEmpty()) {
            sb.appendLine("  Excluded projects    : ${excludedProjects.joinToString(", ")}")
        }
        if (excludedSourceSets.isNotEmpty() || excludedProjects.isNotEmpty()) sb.appendLine()

        val errorCount = rules.count { severityOf(severityMap, it.propertyKey) == "error" }
        val warnCount  = rules.count { severityOf(severityMap, it.propertyKey) == "warning" }
        sb.appendLine("  Summary: $errorCount error(s), $warnCount warning(s) — ${rules.size} rules total")
        sb.appendLine()
        sb.appendLine(sep)
        sb.appendLine(String.format("  %-12s  %-7s  %-6s  %s", "Code", "Severity", "§", "Rule"))
        sb.appendLine(sep)

        for (rule in rules) {
            val sev = severityOf(severityMap, rule.propertyKey).uppercase().padEnd(7)
            sb.appendLine(String.format("  %-12s  %-7s  %-6s  %s", rule.ruleCode, sev, rule.section, rule.title))
        }

        sb.appendLine(sep)
        sb.appendLine()
        sb.appendLine("  Documentation: $docBase")
        sb.appendLine("═".repeat(72))
        return sb.toString()
    }

    // ─── HTML generator ─────────────────────────────────────────────────────

    private fun generateHtml(
        project: String,
        version: String,
        timestamp: String,
        severityMap: Map<String, String>,
        excludedSourceSets: List<String>,
        excludedProjects: List<String>,
    ): String {
        val errorCount = rules.count { severityOf(severityMap, it.propertyKey) == "error" }
        val warnCount  = rules.count { severityOf(severityMap, it.propertyKey) == "warning" }

        val rulesRows = rules.joinToString("\n") { rule ->
            val sev        = severityOf(severityMap, rule.propertyKey)
            val badgeClass = if (sev == "error") "badge-error" else "badge-warning"
            val docUrl     = "$docBase#${rule.docAnchor}"
            """        <tr>
          <td><code>${rule.ruleCode}</code></td>
          <td>§${rule.section}</td>
          <td>${rule.title}</td>
          <td><span class="badge $badgeClass">${sev.uppercase()}</span></td>
          <td><a href="$docUrl" target="_blank">See docs →</a></td>
        </tr>"""
        }

        val exclusionsHtml = buildString {
            if (excludedSourceSets.isNotEmpty()) {
                append("""<p class="excl"><strong>Excluded source sets:</strong> ${excludedSourceSets.joinToString(", ")}</p>""")
                append("\n")
            }
            if (excludedProjects.isNotEmpty()) {
                append("""<p class="excl"><strong>Excluded projects:</strong> ${excludedProjects.joinToString(", ")}</p>""")
                append("\n")
            }
        }

        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Structured Coroutines Report — $project</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
           background: #f8f9fa; color: #212529; padding: 24px; font-size: 14px; }
    .container { max-width: 980px; margin: 0 auto; background: #fff;
                 border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,.1); overflow: hidden; }
    header { background: #1a1a2e; color: #fff; padding: 24px 32px; }
    header h1 { font-size: 1.35rem; font-weight: 600; }
    header .meta { margin-top: 8px; font-size: .82rem; opacity: .72; }
    .summary { display: flex; gap: 16px; padding: 18px 32px;
               background: #f1f3f5; border-bottom: 1px solid #dee2e6; }
    .stat { text-align: center; padding: 10px 22px; border-radius: 6px;
            background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.08); min-width: 90px; }
    .stat .value { font-size: 1.75rem; font-weight: 700; line-height: 1; }
    .stat .label { font-size: .7rem; color: #6c757d; margin-top: 4px;
                   text-transform: uppercase; letter-spacing: .06em; }
    .stat.errors   .value { color: #dc3545; }
    .stat.warnings .value { color: #fd7e14; }
    .stat.total    .value { color: #0d6efd; }
    .content { padding: 24px 32px; }
    .excl { font-size: .85rem; color: #6c757d; margin-bottom: 6px; }
    table { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: .875rem; }
    th { background: #f8f9fa; color: #495057; font-weight: 600;
         padding: 10px 14px; text-align: left; border-bottom: 2px solid #dee2e6;
         white-space: nowrap; }
    td { padding: 10px 14px; border-bottom: 1px solid #f1f3f5; vertical-align: middle; }
    tr:last-child td { border-bottom: none; }
    tr:hover td { background: #f8f9fa; }
    code { background: #e9ecef; padding: 2px 6px; border-radius: 3px;
           font-size: .85em; font-family: 'JetBrains Mono', 'Fira Code', monospace; }
    .badge { display: inline-block; padding: 3px 8px; border-radius: 4px;
             font-size: .72rem; font-weight: 700; text-transform: uppercase;
             letter-spacing: .06em; }
    .badge-error   { background: #fff1f0; color: #cf1322; border: 1px solid #ffa39e; }
    .badge-warning { background: #fffbe6; color: #874d00; border: 1px solid #ffe58f; }
    a { color: #0d6efd; text-decoration: none; }
    a:hover { text-decoration: underline; }
    footer { padding: 14px 32px; background: #f8f9fa;
             border-top: 1px solid #dee2e6; font-size: .78rem; color: #6c757d; }
  </style>
</head>
<body>
<div class="container">
  <header>
    <h1>Structured Coroutines — Plugin Configuration Report</h1>
    <div class="meta">
      Project: <strong>$project</strong>&nbsp;&nbsp;|&nbsp;&nbsp;Plugin version:
      <strong>$version</strong>&nbsp;&nbsp;|&nbsp;&nbsp;Generated: $timestamp
    </div>
  </header>

  <div class="summary">
    <div class="stat errors"><div class="value">$errorCount</div><div class="label">Errors</div></div>
    <div class="stat warnings"><div class="value">$warnCount</div><div class="label">Warnings</div></div>
    <div class="stat total"><div class="value">${rules.size}</div><div class="label">Rules</div></div>
  </div>

  <div class="content">
    $exclusionsHtml
    <table>
      <thead>
        <tr>
          <th>Code</th>
          <th>§</th>
          <th>Rule</th>
          <th>Severity</th>
          <th>Reference</th>
        </tr>
      </thead>
      <tbody>
$rulesRows
      </tbody>
    </table>
  </div>

  <footer>
    <a href="$docBase" target="_blank">Best Practices Documentation</a>
    &nbsp;·&nbsp;
    <a href="https://github.com/santimattius/structured-coroutines" target="_blank">GitHub</a>
    &nbsp;·&nbsp; Structured Coroutines v$version
  </footer>
</div>
</body>
</html>
"""
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun severityOf(map: Map<String, String>, key: String): String =
        map[key]?.lowercase() ?: "error"
}
