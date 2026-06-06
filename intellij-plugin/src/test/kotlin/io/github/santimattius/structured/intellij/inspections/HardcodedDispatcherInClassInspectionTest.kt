package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HardcodedDispatcherInClassInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = HardcodedDispatcherInClassInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.hardcoded.dispatcher.display.name")
    }
}
