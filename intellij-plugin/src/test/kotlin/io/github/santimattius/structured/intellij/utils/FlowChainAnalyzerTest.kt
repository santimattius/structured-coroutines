package io.github.santimattius.structured.intellij.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowChainAnalyzerTest {

    @Test
    fun `report includes catch when catch operator present`() {
        val findings = listOf(
            FlowChainAnalyzer.FlowChainFinding("✅", "catch — error handling present"),
        )
        val report = FlowChainAnalyzer.formatReport(findings)
        assertThat(report).contains("catch — error handling present")
    }

    @Test
    fun `report warns when distinctUntilChanged missing`() {
        val findings = listOf(
            FlowChainAnalyzer.FlowChainFinding("⚠️", "missing distinctUntilChanged — potential redundant emissions"),
        )
        val report = FlowChainAnalyzer.formatReport(findings)
        assertThat(report).contains("missing distinctUntilChanged")
    }

    @Test
    fun `report notes flatMapLatest semantics`() {
        val findings = listOf(
            FlowChainAnalyzer.FlowChainFinding("ℹ️", "flatMapLatest — last-wins semantics (suitable for search, not for downloads)"),
        )
        val report = FlowChainAnalyzer.formatReport(findings)
        assertThat(report).contains("flatMapLatest")
        assertThat(report).contains("Flow chain analysis:")
    }
}
