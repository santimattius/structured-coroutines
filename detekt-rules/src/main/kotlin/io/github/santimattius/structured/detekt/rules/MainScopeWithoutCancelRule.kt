/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.github.santimattius.structured.detekt.utils.MainScopeCleanupHeuristic
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtClass

/**
 * [KMP_003] — MainScope without scope.cancel() in cleanup methods.
 */
class MainScopeWithoutCancelRule(config: Config = Config.empty) : Rule(
    config,
    description = "[KMP_003] MainScope must be cancelled in onDestroy/onCleared/dispose. " +
            "See: ${DetektDocUrl.buildDocLink("113-kmp_003--mainscopewithoutcancel")}",
) {

    override val ruleName: RuleName get() = RuleName("MainScopeWithoutCancel")

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(klass)) return
        MainScopeCleanupHeuristic.findIssues(klass).forEach { issue ->
            report(
                Finding(
                    entity = Entity.from(issue.property),
                    message = "[KMP_003] MainScope property '${issue.property.name}' — ${issue.reason}. " +
                        "Call scope.cancel() in cleanup. " +
                        "See: ${DetektDocUrl.buildDocLink("113-kmp_003--mainscopewithoutcancel")}",
                ),
            )
        }
    }
}
