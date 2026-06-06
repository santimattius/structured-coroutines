package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChannelFlowVsCallbackFlowInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = ChannelFlowVsCallbackFlowInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.channelflow.vs.callbackflow.display.name")
    }
}
