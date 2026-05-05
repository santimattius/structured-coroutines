/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import io.github.santimattius.structured.lint.utils.AndroidLintUtils
import io.github.santimattius.structured.lint.utils.ComposePsiUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression

/**
 * [COMPOSE_001] — `Flow.collectAsState()` in Compose keeps emitting while the Composition is deactivated.
 * Prefer `collectAsStateWithLifecycle()` from lifecycle-runtime-compose for Android shells.
 */
class CollectAsStateWithoutLifecycleDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "CollectAsStateWithoutLifecycle",
            briefDescription = "collectAsState without lifecycle awareness",
            explanation = """
                [COMPOSE_001] Plain `collectAsState()` continues collecting Flow emissions even when the
                screen or window is inactive. On Android screens, prefer
                `collectAsStateWithLifecycle()` (lifecycle-runtime-compose) so collection aligns with lifecycle.

                See: ${LintDocUrl.buildDocLink("83-compose_001--collectasstate-without-lifecycle-awareness")}
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CollectAsStateWithoutLifecycleDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("collectAsState")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: com.intellij.psi.PsiMethod) {
        if (AndroidLintUtils.isTestFile(context, node)) return

        val ktCall = node.sourcePsi as? KtCallExpression ?: return
        val ktFile = ktCall.containingKtFile

        if (!ComposePsiUtils.importsComposeRuntime(ktFile)) return
        if (!ComposePsiUtils.importsKotlinxCoroutinesFlow(ktFile)) return

        if (!ComposePsiUtils.hasComposableAncestor(ktCall)) return
        if (ComposePsiUtils.hasPreviewAncestor(ktCall)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "[COMPOSE_001] Prefer `collectAsStateWithLifecycle()` for lifecycle-aware Flow collection in Compose UI. " +
                "See: ${LintDocUrl.buildDocLink("83-compose_001--collectasstate-without-lifecycle-awareness")}",
        )
    }
}
