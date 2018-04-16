package tech.pronghorn.coroutines.core

class RunnableBlockInWorker(private val block: () -> Unit): RunnableInWorker {
    override fun runInWorker() = block()
}
