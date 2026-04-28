package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.diagnostics.Severity
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginConfigurationInteropOptionsTest {

    private fun pluginKey(shortKey: String): String =
        "plugin:io.github.santimattius.structured-coroutines:$shortKey"

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
            CompilerConfigurationKey.create(pluginKey("suspendCoroutineWithoutCancellation")),
            "warning",
        )
        cfg.put(
            CompilerConfigurationKey.create(pluginKey("callbackFlowWithoutAwaitClose")),
            "warning",
        )
        val pc = PluginConfiguration(cfg)
        assertEquals(Severity.WARNING, pc.suspendCoroutineWithoutCancellation)
        assertEquals(Severity.WARNING, pc.callbackFlowWithoutAwaitClose)
    }
}
