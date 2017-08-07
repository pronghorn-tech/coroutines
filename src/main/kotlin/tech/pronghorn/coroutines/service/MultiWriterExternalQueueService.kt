package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.ExternalQueue
import java.util.concurrent.atomic.AtomicBoolean

abstract class MultiWriterExternalQueueService<WorkType>(queueCapacity: Int = 1024) : QueueService<WorkType>() {
    private val queue = ExternalQueue<WorkType>(queueCapacity, this)

//        TODO("WorkItem<WorkType>")

    private val queueWriterGiven = AtomicBoolean(false)

    protected val queueReader = queue.queueReader

    override fun getQueueWriter(): ExternalQueue.ExternalQueueWriter<WorkType> {
        if (queueWriterGiven.compareAndSet(false, true)) {
            return queue.queueWriter
        }
        else {
            throw Exception("Only one queue writer can be created for this service.")
        }
    }

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
