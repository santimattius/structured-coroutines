/**
 * Stub signatures for lint unit tests so that type resolution works.
 * Used by TestLintTask.files(stub, ...) when the test code imports these symbols.
 */
package io.github.santimattius.structured.lint

import com.android.tools.lint.checks.infrastructure.TestFile
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

    val kotlinxCoroutinesFlow = """
        package kotlinx.coroutines.flow
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Job

        interface Flow<out T>
        interface StateFlow<out T> : Flow<T>
        interface FlowCollector<in T> {
            suspend fun emit(value: T)
        }

        fun <T> flow(block: suspend FlowCollector<T>.() -> Unit): Flow<T> = error("stub")
        fun <T> flowOf(value: T): Flow<T> = error("stub")
        fun <T> flowOf(vararg values: T): Flow<T> = error("stub")

        fun <T, R> Flow<T>.map(transform: suspend (value: T) -> R): Flow<R> = error("stub")
        fun <T> Flow<T>.catch(
            handler: suspend FlowCollector<T>.(cause: Throwable) -> Unit
        ): Flow<T> = error("stub")

        suspend fun <T> Flow<T>.collect(action: suspend (value: T) -> Unit): Unit = error("stub")
        suspend fun <T> Flow<T>.collectLatest(action: suspend (value: T) -> Unit): Unit = error("stub")
        fun <T> Flow<T>.launchIn(scope: CoroutineScope): Job = error("stub")

        sealed class SharingStarted {
            object Eagerly : SharingStarted()
            companion object {
                fun WhileSubscribed(stopTimeoutMillis: Long): SharingStarted = error("stub")
            }
        }

        fun <T> Flow<T>.stateIn(
            scope: CoroutineScope,
            started: SharingStarted,
            initialValue: T,
        ): StateFlow<T> = error("stub")
    """.trimIndent()

    val androidxLifecycle = """
        package androidx.lifecycle
        import kotlinx.coroutines.CoroutineScope
        open class ViewModel
        val ViewModel.viewModelScope: CoroutineScope get() = error("stub")
    """.trimIndent()

    val composeRuntime = """
        package androidx.compose.runtime
        import kotlinx.coroutines.CoroutineScope
        fun rememberCoroutineScope(): CoroutineScope = error("stub")
    """.trimIndent()

    /** Composable stubs + Flow.collectAsState extensions for Compose ([COMPOSE_001]) tests */
    val androidxComposeRuntimeCollect = """
        package androidx.compose.runtime

        import kotlinx.coroutines.flow.Flow
        import kotlinx.coroutines.flow.StateFlow

        annotation class Composable

        interface State<out T> {
            val value: T
        }

        @Composable
        fun <T> Flow<T>.collectAsState(initial: T): State<T> = error("stub")

        /** Preferred overload used by `@Composable val x by uiState.collectAsState()` — StateFlow is Flow. */
        @Composable
        fun <T : Any> StateFlow<T>.collectAsState(): State<T> = error("stub")
    """.trimIndent()

    val androidxComposeUiPreview = """
        package androidx.compose.ui.tooling.preview

        annotation class Preview
        annotation class MultiPreview
    """.trimIndent()

    fun composeRuntimeCollectAndFlow(): List<TestFile> = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented(),
        TestFiles.kotlin(kotlinxCoroutinesFlow).indented(),
        TestFiles.kotlin(androidxComposeRuntimeCollect).indented(),
        TestFiles.kotlin(androidxComposeUiPreview).indented(),
    )

    fun all() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented(),
        TestFiles.kotlin(kotlinxCoroutinesChannels).indented(),
        TestFiles.kotlin(kotlinxCoroutinesFlow).indented(),
        TestFiles.kotlin(androidxLifecycle).indented()
    )

    fun coroutinesAndFlow() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented(),
        TestFiles.kotlin(kotlinxCoroutinesFlow).indented()
    )

    fun coroutinesOnly() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented()
    )

    fun coroutinesAndChannels() = listOf(
        TestFiles.kotlin(kotlinxCoroutines).indented(),
        TestFiles.kotlin(kotlinxCoroutinesChannels).indented()
    )
}
