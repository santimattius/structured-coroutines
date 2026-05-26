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
import io.github.santimattius.structured.lint.utils.KotlinCommonSourceLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import io.github.santimattius.structured.lint.utils.importsKotlinxCoroutines
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * [KMP_002] — runBlocking in commonMain/commonTest.
 */
class RunBlockingInCommonMainDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "RunBlockingInCommonMain",
            briefDescription = "runBlocking in Kotlin common source",
            explanation = """
                [KMP_002] runBlocking is not available on Kotlin/JS and is unsafe on Native main thread.
                Use suspend APIs in common code.

                See: ${LintDocUrl.buildDocLink("112-kmp_002--runblockingincommonmain")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                RunBlockingInCommonMainDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val path = context.file.path.replace('\\', '/')
                if (!KotlinCommonSourceLintUtils.absolutePathLooksLikeKotlinCommonLikeSource(path)) return

                val ktFile = node.sourcePsi?.containingFile as? KtFile ?: return
                if (!ktFile.importsKotlinxCoroutines()) return
                if (!CoroutineLintUtils.isRunBlockingCall(node)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[KMP_002] runBlocking in common Kotlin source — not portable to JS/Native. " +
                        "See: ${LintDocUrl.buildDocLink("112-kmp_002--runblockingincommonmain")}",
                )
            }
        }
}
