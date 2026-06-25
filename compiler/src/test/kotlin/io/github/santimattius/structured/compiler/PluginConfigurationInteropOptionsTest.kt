package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.Severity
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(CompilerConfiguration.Internals::class)
class PluginConfigurationInteropOptionsTest {

    @Test
    fun `suspendCoroutineWithoutCancellation defaults to ERROR`() {
        val cfg = CompilerConfiguration()
        val pc = PluginConfiguration(cfg)
        assertEquals(Severity.ERROR, pc.suspendCoroutineWithoutCancellation)
    }

    @Test
    fun `callbackFlowWithoutAwaitClose defaults to ERROR`() {
        val cfg = CompilerConfiguration()
        val pc = PluginConfiguration(cfg)
        assertEquals(Severity.ERROR, pc.callbackFlowWithoutAwaitClose)
    }

    @Test
    fun `interop severities honor compiler plugin options`() {
        val cfg = CompilerConfiguration()
        cfg.put(
            PluginConfiguration.OPTIONS_KEY,
            mapOf(
                "suspendCoroutineWithoutCancellation" to "warning",
                "callbackFlowWithoutAwaitClose" to "warning",
            ),
        )
        val pc = PluginConfiguration(cfg)
        assertEquals(Severity.WARNING, pc.suspendCoroutineWithoutCancellation)
        assertEquals(Severity.WARNING, pc.callbackFlowWithoutAwaitClose)
    }
}
