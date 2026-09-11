/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty

/**
 * [FLOW_011] — [MutableSharedFlow] with default args for one-shot UI events.
 */
class SharedFlowForOneshotEventsRule(config: Config = Config.empty) : Rule(
    config,
    description = "[FLOW_011] Prefer Channel(BUFFERED).receiveAsFlow() for one-shot events. " +
            "See: ${DetektDocUrl.buildDocLink("911-flow_011--sharedflow-for-oneshot-events")}",
) {

    override val ruleName: RuleName get() = RuleName("SharedFlowForOneshotEvents")

    private val oneShotNamePattern = Regex("""(event|command|effect)""", RegexOption.IGNORE_CASE)

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!CoroutinesImportFilter.elementImportsCoroutinesOrFlow(property)) return
        val path = property.containingKtFile.virtualFilePath
        val name = property.containingKtFile.name
        if (CoroutineDetektUtils.isTestFile(path) || CoroutineDetektUtils.isTestFile(name)) return

        val propName = property.name ?: return
        if (!oneShotNamePattern.containsMatchIn(propName)) return

        val init = property.initializer as? KtCallExpression ?: return
        val callee = init.calleeExpression?.text ?: return
        if (callee != "MutableSharedFlow" && !callee.endsWith(".MutableSharedFlow")) return

        if (hasNonDefaultBuffer(init.text)) return

        report(
            Finding(
                entity = Entity.from(property),
                message = "[FLOW_011] MutableSharedFlow(replay=0) for one-shot events may drop emissions — " +
                    "use Channel(BUFFERED).receiveAsFlow(). " +
                    "See: ${DetektDocUrl.buildDocLink("911-flow_011--sharedflow-for-oneshot-events")}",
            ),
        )
    }

    private fun hasNonDefaultBuffer(argsText: String): Boolean {
        val replayPositive = Regex("""replay\s*=\s*([1-9]\d*)""")
        val extraPositive = Regex("""extraBufferCapacity\s*=\s*([1-9]\d*)""")
        return replayPositive.containsMatchIn(argsText) || extraPositive.containsMatchIn(argsText)
    }
}
