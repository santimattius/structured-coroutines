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
 * [FLOW_006] — SharingStarted.Eagerly with lifecycle-bound scopes in stateIn.
 */
class StateInWithEagerlyStrategyDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "StateInWithEagerlyStrategy",
            briefDescription = "stateIn with SharingStarted.Eagerly on lifecycle scope",
            explanation = """
                [FLOW_006] stateIn(..., SharingStarted.Eagerly, ...) on viewModelScope or lifecycleScope
                starts upstream collection before any UI collector is active. Prefer WhileSubscribed(5_000).

                See: ${LintDocUrl.buildDocLink("97-flow_006--stateinwitheagerlystrategy")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                StateInWithEagerlyStrategyDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!CoroutineLintUtils.isStateInWithEagerlyOnLifecycleScope(node)) return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[FLOW_006] stateIn with SharingStarted.Eagerly on a lifecycle scope. " +
                        "Prefer SharingStarted.WhileSubscribed(5_000). " +
                        "See: ${LintDocUrl.buildDocLink("97-flow_006--stateinwitheagerlystrategy")}",
                )
            }
        }
}
