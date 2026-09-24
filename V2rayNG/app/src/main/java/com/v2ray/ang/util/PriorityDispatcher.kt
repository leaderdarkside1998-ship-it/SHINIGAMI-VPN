package com.v2ray.ang.util

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Builds a dedicated single-thread [CoroutineDispatcher] whose thread runs at
 * [Process.THREAD_PRIORITY_FOREGROUND] instead of the default (background) priority.
 *
 * [Dispatchers.IO] is a large shared pool used by every part of the app (and, indirectly, by
 * other apps' IPC into the system), and its worker threads run at normal/background OS priority.
 * When a CPU-heavy foreground app is running at the same time (a game is the common case), the
 * scheduler can starve those background-priority threads for long stretches, so anything
 * dispatched on [Dispatchers.IO] -- like the connection-test probe or a periodic notification
 * update -- can be delayed well beyond its own workload, making it look "weak"/unresponsive even
 * though nothing about the work itself changed.
 *
 * This gives latency-sensitive, low-frequency background work (a ping test, a notification
 * refresh) its own thread at foreground priority so it keeps being scheduled promptly under that
 * kind of CPU pressure, without moving everything else in the app off the shared IO pool.
 *
 * @param name Thread name, for debugging (visible in profilers/ANR traces).
 */
fun newForegroundPriorityDispatcher(name: String): CoroutineDispatcher {
    val threadFactory = ThreadFactory { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            runnable.run()
        }, name).apply { isDaemon = true }
    }
    return Executors.newSingleThreadExecutor(threadFactory).asCoroutineDispatcher()
}
