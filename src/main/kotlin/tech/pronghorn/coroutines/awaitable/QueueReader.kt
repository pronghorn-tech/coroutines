package tech.pronghorn.coroutines.awaitable

interface QueueReader<out T> : Awaitable<T> {
    fun poll(): T?
}
