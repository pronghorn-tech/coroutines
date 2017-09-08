package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.ExternalQueue
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.util.stackTraceToString

abstract class MultiWriterExternalQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = ExternalQueue<WorkType>(queueCapacity, this)

    protected val queueReader = queue.queueReader

    override fun getQueueWriter(): ExternalQueue.ExternalQueueWriter<WorkType> = queue.queueWriter

    abstract suspend fun process(work: WorkType)

    override suspend fun run() {
        while (isRunning) {
            val workItem = await(queueReader)
            if (shouldYield()) {
                yieldAsync()
            }

            try {
                process(workItem)
            }
            catch (ex: Exception) {
                logger.error { "Queue service threw exception: ${ex.stackTraceToString()}" }
            }
        }
    }
}
