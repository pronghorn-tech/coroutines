package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.QueueWriter

/**
 * Calls process() for each work item in the queue.
 * Actual implementations may or not have mechanisms for cross thread access.
 */
abstract class QueueService<WorkType> : Service() {
    /**
     * Provides QueueWriters to external users that need to append to this service's queue.
     * Implementations may limit access to a writer.
     */
    abstract fun getQueueWriter(): QueueWriter<WorkType>

    /**
     * Allows a service to decide when yielding to the worker happens by returning true when yielding is desired.
     */
    open fun shouldYield(): Boolean = false
}
