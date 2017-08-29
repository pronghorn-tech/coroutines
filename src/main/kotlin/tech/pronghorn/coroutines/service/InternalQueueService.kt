package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.InternalQueue

/**
 * This type of service is strictly for use within a single worker. process() is allowed to be
 * partially completed for an individual work item, indicated by the Boolean response from process().
 * In the event that a work item is partially processed, it is re-added immediately to the end of the queue.
 */
abstract class InternalQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = InternalQueue<WorkType>(queueCapacity)
    private val queueReader = queue.queueReader

    override fun getQueueWriter(): InternalQueue.InternalQueueWriter<WorkType> {
        assert(worker.isSchedulerThread())
        return queue.queueWriter
    }

    abstract suspend fun process(work: WorkType): Boolean

    override suspend fun run(): Unit {
        var workItem = queueReader.nextAsync()
        while (isRunning) {
            if (shouldYield()) {
                yieldAsync()
            }

            val finished = process(workItem)

            if (!finished) {
                val prevWorkItem = workItem
                workItem = queueReader.pollAndAdd(workItem)
                if(workItem == prevWorkItem){
                    yieldAsync()
                }
            }
            else {
                workItem = queueReader.poll() ?: queueReader.awaitAsync()
            }
        }
    }
}
