package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedFlowForOneshotEventsInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = SharedFlowForOneshotEventsInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.sharedflow.oneshot.display.name")
    }
}
