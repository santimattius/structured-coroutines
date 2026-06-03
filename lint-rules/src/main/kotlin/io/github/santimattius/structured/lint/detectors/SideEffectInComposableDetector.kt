/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import io.github.santimattius.structured.lint.utils.AndroidLintUtils
import io.github.santimattius.structured.lint.utils.ComposePsiUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import io.github.santimattius.structured.lint.utils.importsComposeRuntime
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * [COMPOSE_003] — side-effect calls directly in composable body.
 */
class SideEffectInComposableDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "SideEffectInComposable",
            briefDescription = "Side effect in composable body",
            explanation = """
                [COMPOSE_003] Side effects in the composable body run on every recomposition. Wrap them in
                SideEffect { }, LaunchedEffect { }, or DisposableEffect { }.

                See: ${LintDocUrl.buildDocLink("85-compose_003--sideeffectincomposable")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SideEffectInComposableDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        private val sideEffectCalleeNames = setOf(
            "logScreen",
            "logEvent",
            "track",
            "trackEvent",
            "log",
        )

        private val sideEffectReceiverHints = setOf(
            "analytics",
            "Analytics",
            "firebaseAnalytics",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (AndroidLintUtils.isTestFile(context, node)) return
                val ktCall = node.sourcePsi as? KtCallExpression ?: return
                val ktFile = ktCall.containingKtFile
                if (!ktFile.importsComposeRuntime()) return
                if (!ComposePsiUtils.hasComposableAncestor(ktCall)) return
                if (ComposePsiUtils.hasPreviewAncestor(ktCall)) return
                if (ComposePsiUtils.isInsideComposeEffectOrEventHandler(ktCall)) return
                if (!looksLikeSideEffectCall(node)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[COMPOSE_003] Side-effect call outside effect block runs on every recomposition. " +
                        "Wrap in SideEffect { }, LaunchedEffect { }, or DisposableEffect { }. " +
                        "See: ${LintDocUrl.buildDocLink("85-compose_003--sideeffectincomposable")}",
                )
            }
        }

    private fun looksLikeSideEffectCall(call: UCallExpression): Boolean {
        val method = call.methodName ?: return false
        if (method in sideEffectCalleeNames) return true
        val receiver = call.receiver?.asSourceString().orEmpty()
        return sideEffectReceiverHints.any { receiver.contains(it) }
    }
}
