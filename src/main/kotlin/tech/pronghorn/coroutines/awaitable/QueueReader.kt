package tech.pronghorn.coroutines.awaitable

interface QueueReader<out T> {
    fun poll(): T?
}
