package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BlockingFutureGetInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = BlockingFutureGetInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.blocking.future.get.display.name")
    }
}
