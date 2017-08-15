package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.ExternalQueue

abstract class MultiWriterExternalQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = ExternalQueue<WorkType>(queueCapacity, this)

    protected val queueReader = queue.queueReader

    override fun getQueueWriter(): ExternalQueue.ExternalQueueWriter<WorkType> = queue.queueWriter

    abstract suspend fun process(work: WorkType): Unit

    override suspend fun run(): Unit {
        while (isRunning) {
            val workItem = queueReader.nextAsync()
            if (shouldYield()) {
                yieldAsync()
            }
            process(workItem)
        }
    }
}
