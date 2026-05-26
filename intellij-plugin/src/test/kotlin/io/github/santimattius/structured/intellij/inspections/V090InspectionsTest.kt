package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class V090InspectionsTest {

    @Test
    fun `SynchronizedInCoroutine inspection is configured`() {
        val i = SynchronizedInCoroutineInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.synchronized.in.coroutine.display.name")
        assertThat(i.isEnabledByDefault).isTrue()
    }

    @Test
    fun `StateInWithEagerlyStrategy inspection is configured`() {
        val i = StateInWithEagerlyStrategyInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.statein.eagerly.display.name")
    }

    @Test
    fun `LaunchInWithUnstructuredScope inspection is configured`() {
        val i = LaunchInWithUnstructuredScopeInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.launchin.unstructured.display.name")
    }

    @Test
    fun `RedundantWithContext inspection is configured`() {
        val i = RedundantWithContextInspection()
        assertThat(i.descriptionKey).isEqualTo("inspection.redundant.withcontext.description")
    }

    @Test
    fun `SideEffectInMapOperator inspection is configured`() {
        val i = SideEffectInMapOperatorInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.sideeffect.in.map.display.name")
    }
}
