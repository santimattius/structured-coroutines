package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoroutineNotCompletedInTestInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = CoroutineNotCompletedInTestInspection()
        assertThat(i.descriptionKey).isEqualTo("inspection.coroutine.not.completed.in.test.description")
    }
}
