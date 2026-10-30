package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class TracingDispatcherJvmTest {

    @Test
    fun threadInfoMatchesWorkerThreadAtCoroutineLevel() = runBlocking {
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(Dispatchers.Default, "default", sink)
        var workerId = -1L
        var workerName = ""
        withContext(d + CoroutineName("probe")) {
            workerId = Thread.currentThread().id
            workerName = Thread.currentThread().name
        }
        val started = sink.snapshot().filterIsInstance<TraceEvent.Started>().first()
        val thread = assertNotNull(started.thread)
        assertEquals(workerId, thread.id)
        // With coroutine debugging on, the thread name carries an " @coroutine#n" suffix while running.
        assertEquals(workerName.substringBefore(" @"), thread.name.substringBefore(" @"))
        assertTrue(thread.name.startsWith("DefaultDispatcher-worker"), thread.name)
        assertEquals("probe", started.coroutineName)
    }
}
