package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlatMapOperatorChoiceInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = FlatMapOperatorChoiceInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.flatmap.operator.choice.display.name")
    }
}
