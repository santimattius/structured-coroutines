/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * [FLOW_007] — Flow.launchIn with GlobalScope or inline CoroutineScope.
 */
class LaunchInWithUnstructuredScopeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "LaunchInWithUnstructuredScope",
            briefDescription = "launchIn with unstructured scope",
            explanation = """
                [FLOW_007] Collecting a Flow with launchIn(GlobalScope) or launchIn(CoroutineScope(...))
                creates orphan work that is never cancelled. Use a lifecycle-bound scope instead.

                See: ${LintDocUrl.buildDocLink("98-flow_007--launchinwithunstructuredscope")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                LaunchInWithUnstructuredScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!CoroutineLintUtils.isLaunchInUnstructuredScope(node)) return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[FLOW_007] launchIn uses an unstructured scope (GlobalScope or inline CoroutineScope). " +
                        "Use viewModelScope, lifecycleScope, or coroutineScope { }. " +
                        "See: ${LintDocUrl.buildDocLink("98-flow_007--launchinwithunstructuredscope")}",
                )
            }
        }
}
