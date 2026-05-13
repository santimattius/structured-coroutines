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
import io.github.santimattius.structured.lint.utils.KotlinCommonSourceLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import io.github.santimattius.structured.lint.utils.importsKotlinxCoroutines
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression

/**
 * [KMP_001] — references to `Dispatchers.IO` under `commonMain` / `commonTest` are invalid on Native/JS.
 */
class DispatchersIOInCommonMainDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DispatchersIOInCommonMain",
            briefDescription = "Dispatchers.IO in Kotlin common source",
            explanation = """
                [KMP_001] `Dispatchers.IO` is not available in Kotlin/Native or Kotlin/JS. Use an injected
                `CoroutineDispatcher`, or `expect`/`actual` to provide a platform-backed I/O dispatcher.

                See: ${LintDocUrl.buildDocLink("111-kmp_001--dispatchersio-in-commonmain")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                DispatchersIOInCommonMainDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        internal fun isDispatchersIOAccess(text: String): Boolean {
            val t = text.removePrefix("kotlinx.coroutines.")
            return t == "Dispatchers.IO" ||
                t.endsWith(".Dispatchers.IO")
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val path = context.file.path.replace('\\', '/')
                if (!KotlinCommonSourceLintUtils.absolutePathLooksLikeKotlinCommonLikeSource(path)) return

                val ktFile = node.sourcePsi?.containingFile as? KtFile ?: return
                if (!ktFile.importsKotlinxCoroutines()) return

                val source = node.asSourceString()
                if (!isDispatchersIOAccess(source)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[KMP_001] `Dispatchers.IO` in common Kotlin source sets will fail on iOS / JS targets. Inject a dispatcher or use expect/actual. " +
                        "See: ${LintDocUrl.buildDocLink("111-kmp_001--dispatchersio-in-commonmain")}",
                )
            }
        }
}
