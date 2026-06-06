package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RememberScopeForInitInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = RememberScopeForInitInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.remember.scope.for.init.display.name")
        assertThat(i.isEnabledByDefault).isTrue()
    }
}
