package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.SharedQueue

abstract class SharedQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = SharedQueue<WorkType>(queueCapacity)
    private val queueReader = queue.queueReader

    override fun getQueueWriter(): SharedQueue.SharedQueueWriter<WorkType> {
        return queue.queueWriter
    }

    abstract suspend fun process(work: WorkType): Boolean

    override suspend fun run() {
        queueReader.poll()
        TODO()
//        var workItem = queueReader.nextAsync()
//        while (isRunning) {
//            if (shouldYield()) {
//                yieldAsync()
//            }
//
//            process(workItem)
//            workItem = queueReader.poll() ?: queueReader.awaitAsync()
//        }
    }
}
