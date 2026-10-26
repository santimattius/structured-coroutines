/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.fixture

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base class for PSI fixture tests (JUnit 3/4 style, run by the Vintage engine next to the
 * JUnit 5 metadata tests).
 *
 * [highlights] loads a Kotlin snippet into the light fixture, enables exactly one inspection and
 * returns only the highlights produced by that inspection.
 */
abstract class InspectionFixtureTestCase : BasePlatformTestCase() {

    protected fun highlights(inspection: LocalInspectionTool, code: String): List<HighlightInfo> {
        myFixture.enableInspections(inspection)
        myFixture.configureByText("Test.kt", code)
        return myFixture.doHighlighting().filter { it.inspectionToolId == inspection.shortName }
    }
}
