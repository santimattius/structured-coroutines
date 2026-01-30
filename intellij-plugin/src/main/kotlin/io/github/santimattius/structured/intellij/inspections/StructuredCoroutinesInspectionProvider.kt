/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.codeInspection.LocalInspectionTool

/**
 * Provider for all structured coroutines inspections.
 *
 * This class registers all inspection classes with the IntelliJ platform.
 */
class StructuredCoroutinesInspectionProvider : InspectionToolProvider {

    override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> {
        return arrayOf(
            GlobalScopeInspection::class.java,
            MainDispatcherMisuseInspection::class.java,
            ScopeReuseAfterCancelInspection::class.java,
            RunBlockingInSuspendInspection::class.java,
            UnstructuredLaunchInspection::class.java,
            AsyncWithoutAwaitInspection::class.java,
            InlineCoroutineScopeInspection::class.java,
            JobInBuilderContextInspection::class.java,
            SuspendInFinallyInspection::class.java,
            CancellationExceptionSwallowedInspection::class.java,
            DispatchersUnconfinedInspection::class.java
        )
    }
}
