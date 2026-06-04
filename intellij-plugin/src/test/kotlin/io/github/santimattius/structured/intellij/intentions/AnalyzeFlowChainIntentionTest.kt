package io.github.santimattius.structured.intellij.intentions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalyzeFlowChainIntentionTest {

    @Test
    fun `intention can be instantiated`() {
        val intention = AnalyzeFlowChainIntention()
        assertThat(intention).isNotNull()
        assertThat(intention.familyName).isNotBlank()
    }
}
