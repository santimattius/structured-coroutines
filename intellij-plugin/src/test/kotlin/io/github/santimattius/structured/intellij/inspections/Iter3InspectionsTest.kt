package io.github.santimattius.structured.intellij.inspections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Configuration smoke tests for Iteration 3 (v1.0.0 P3) IDE inspections. */
class Iter3InspectionsTest {

    @Test
    fun `RememberScopeForInit inspection is configured`() {
        val i = RememberScopeForInitInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.remember.scope.for.init.display.name")
        assertThat(i.isEnabledByDefault).isTrue()
    }

    @Test
    fun `HardcodedDispatcherInClass inspection is configured`() {
        val i = HardcodedDispatcherInClassInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.hardcoded.dispatcher.display.name")
    }

    @Test
    fun `CoroutineNotCompletedInTest inspection is configured`() {
        val i = CoroutineNotCompletedInTestInspection()
        assertThat(i.descriptionKey).isEqualTo("inspection.coroutine.not.completed.in.test.description")
    }

    @Test
    fun `FlatMapOperatorChoice inspection is configured`() {
        val i = FlatMapOperatorChoiceInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.flatmap.operator.choice.display.name")
    }

    @Test
    fun `SharedFlowForOneshotEvents inspection is configured`() {
        val i = SharedFlowForOneshotEventsInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.sharedflow.oneshot.display.name")
    }

    @Test
    fun `ChannelFlowVsCallbackFlow inspection is configured`() {
        val i = ChannelFlowVsCallbackFlowInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.channelflow.vs.callbackflow.display.name")
    }

    @Test
    fun `BlockingFutureGet inspection is configured`() {
        val i = BlockingFutureGetInspection()
        assertThat(i.displayNameKey).isEqualTo("inspection.blocking.future.get.display.name")
    }
}
