package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.ExternalQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This variety of queue service supports queueing from worker other than the one running this service.
 * Because of this, functionality is limited
 */
abstract class SingleWriterExternalQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = ExternalQueue<WorkType>(queueCapacity, this)

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

    abstract suspend protected fun process(work: WorkType)

    override suspend fun run(): Unit {
        while (isRunning) {
            val workItem = queueReader.nextAsync()
            if (shouldYield()) {
                yieldAsync()
            }
//            val preProcess = System.currentTimeMillis()
            process(workItem)
//            val postProcess = System.currentTimeMillis()
//            if(postProcess - preProcess > 10){
//                logger.error("Processing in ${this.javaClass.name} took ${postProcess - preProcess} ms")
//            }
        }
    }
}
