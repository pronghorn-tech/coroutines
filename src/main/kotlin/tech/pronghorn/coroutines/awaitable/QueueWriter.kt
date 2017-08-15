package tech.pronghorn.coroutines.awaitable

interface QueueWriter<in T> {
    fun offer(value: T): Boolean
}
