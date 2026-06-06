package io.github.santimattius.structured.intellij.inspections

import io.github.santimattius.structured.intellij.quickfixes.ReplaceCollectAsStateWithLifecycleQuickFix
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CollectAsStateWithoutLifecycleInspectionTest {

    @Test
    fun `inspection is configured`() {
        val i = CollectAsStateWithoutLifecycleInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.collect.as.state.lifecycle.display.name")
        assertThat(i.isEnabledByDefault).isTrue()
    }

    @Test
    fun `lifecycle quick-fix is available`() {
        assertThat(ReplaceCollectAsStateWithLifecycleQuickFix().familyName).isNotBlank()
    }
}
