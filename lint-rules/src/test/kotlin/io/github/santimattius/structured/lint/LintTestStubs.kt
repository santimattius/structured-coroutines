/**
 * Stub signatures for lint unit tests so that type resolution works.
 * Used by TestLintTask.files(stub, ...) when the test code imports these symbols.
 */
package io.github.santimattius.structured.lint

import com.android.tools.lint.checks.infrastructure.TestFiles

object LintTestStubs {

    val kotlinxCoroutines = """
        package kotlinx.coroutines
        import kotlin.coroutines.CoroutineContext
        interface CoroutineScope
        interface Job : CoroutineContext.Element
        suspend fun delay(timeMillis: Long) {}
        fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T = error("stub")
        object GlobalScope : CoroutineScope
        fun CoroutineScope.launch(context: CoroutineContext = error("stub"), block: suspend CoroutineScope.() -> Unit): Job = error("stub")
        fun CoroutineScope.async(context: CoroutineContext = error("stub"), block: suspend CoroutineScope.() -> Any): Any = error("stub")
        fun Job(parent: Job? = null): Job = error("stub")
        fun SupervisorJob(parent: Job? = null): Job = error("stub")
        object Dispatchers {
            object Unconfined
            object Default
            object Main
            object IO
        }
    """.trimIndent()

    val kotlinxCoroutinesChannels = """
        package kotlinx.coroutines.channels
        interface Channel<E>
        interface ReceiveChannel<out E>
        fun <E> Channel(capacity: Int = 0): Channel<E> = error("stub")
        fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit): Unit = error("stub")
        fun <E> ReceiveChannel<E>.close(): Unit = error("stub")
    """.trimIndent()

    val androidxLifecycle = """
        package androidx.lifecycle
        import kotlinx.coroutines.CoroutineScope
        open class ViewModel
        val ViewModel.viewModelScope: CoroutineScope get() = error("stub")
    """.trimIndent()

    fun all() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented(),
        TestFiles.kotlin(kotlinxCoroutinesChannels).indented(),
        TestFiles.kotlin(androidxLifecycle).indented()
    )

    fun coroutinesOnly() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented()
    )

    fun coroutinesAndChannels() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented(),
        TestFiles.kotlin(kotlinxCoroutinesChannels).indented()
    )
}
