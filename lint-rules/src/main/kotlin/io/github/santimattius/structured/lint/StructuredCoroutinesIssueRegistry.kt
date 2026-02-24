/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import io.github.santimattius.structured.lint.detectors.*

/**
 * Registry for all Structured Coroutines Lint issues.
 * 
 * This registry is automatically discovered by Android Lint through the
 * META-INF/services/com.android.tools.lint.client.api.IssueRegistry file.
 */
class StructuredCoroutinesIssueRegistry : IssueRegistry() {
    
    override val issues: List<com.android.tools.lint.detector.api.Issue>
        get() = listOf(
            // Fase 3.1: Reglas Básicas
            GlobalScopeUsageDetector.ISSUE,
            InlineCoroutineScopeDetector.ISSUE,
            RunBlockingInSuspendDetector.ISSUE,
            DispatchersUnconfinedDetector.ISSUE,
            CancellationExceptionSubclassDetector.ISSUE,
            
            // Fase 3.2: Reglas Android-Específicas
            MainDispatcherMisuseDetector.ISSUE,
            ViewModelScopeLeakDetector.ISSUE,
            LifecycleAwareScopeDetector.ISSUE,
            
            // Fase 3.3: Reglas Avanzadas
            JobInBuilderContextDetector.ISSUE,
            SuspendInFinallyDetector.ISSUE,
            CancellationExceptionSwallowedDetector.ISSUE,
            AsyncWithoutAwaitDetector.ISSUE,
            
            // Fase 3.4: Reglas Adicionales
            UnstructuredLaunchDetector.ISSUE,
            RedundantLaunchInCoroutineScopeDetector.ISSUE,
            RunBlockingWithDelayInTestDetector.ISSUE,
            LoopWithoutYieldDetector.ISSUE,
            ScopeReuseAfterCancelDetector.ISSUE,
            ChannelNotClosedDetector.ISSUE,
            ConsumeEachMultipleConsumersDetector.ISSUE,
        )
    
    override val api: Int = CURRENT_API
    
    override val minApi: Int = 1 // Works with all API levels
    
    override val vendor: com.android.tools.lint.client.api.Vendor
        get() = com.android.tools.lint.client.api.Vendor(
            vendorName = "Santiago Mattiauda",
            identifier = "io.github.santimattius"
        )
}
