package io.github.santimattius.structured.runtime

@OptIn(ExperimentalStructuredCoroutines::class)
internal actual fun currentThreadInfo(): ThreadInfo? {
    val thread = Thread.currentThread()
    @Suppress("DEPRECATION") // Thread.getId is the portable choice across JDK 17 and Android.
    return ThreadInfo(thread.id, thread.name)
}
