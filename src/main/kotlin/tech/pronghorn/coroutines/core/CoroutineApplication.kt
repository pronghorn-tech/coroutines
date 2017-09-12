package tech.pronghorn.coroutines.core

import tech.pronghorn.plugins.concurrentSet.ConcurrentSetPlugin
import tech.pronghorn.plugins.logging.LoggingPlugin

abstract class CoroutineApplication<T : CoroutineWorker>(protected val workerCount: Int) {
    protected val logger = LoggingPlugin.get(javaClass)
    protected val workers = ConcurrentSetPlugin.get<T>()

    var isRunning = false
        private set

    abstract protected fun spawnWorker(): T

    open fun onStart() = Unit

    open fun onShutdown() = Unit

    fun start() {
        for (x in 1..workerCount) {
            val worker = spawnWorker()
            workers.add(worker)
        }
        isRunning = true
        onStart()
        workers.forEach(CoroutineWorker::start)
    }

    fun shutdown() {
        isRunning = false
        onShutdown()
        try {
            workers.forEach(CoroutineWorker::shutdown)
        }
        catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
