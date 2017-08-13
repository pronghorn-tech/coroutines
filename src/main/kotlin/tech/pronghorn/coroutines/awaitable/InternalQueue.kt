package tech.pronghorn.coroutines.awaitable

import mu.KotlinLogging
import tech.pronghorn.plugins.spscQueue.SpscQueuePlugin
import tech.pronghorn.util.roundToPowerOfTwo
import java.util.*

interface QueueWriter<in T> {
    fun offer(value: T): Boolean
}

interface QueueReader<out T> {
    fun poll(): T?
}

class InternalQueue<T>(private val queue: Queue<T>) {
    val capacity = queue.size

    constructor(capacity: Int) : this(SpscQueuePlugin.get<T>(capacity)) {
        if (capacity < 4) {
            throw Exception("Queue size must be at least four.")
        }
        else if (roundToPowerOfTwo(capacity) != capacity) {
            throw Exception("Queue sizes must be powers of two.")
        }
    }

    val queueReader = InternalQueueReader<T>(this)
    val queueWriter = InternalQueueWriter<T>(this)

    private var emptyPromise: InternalFuture.InternalPromise<T>? = null
    private var fullPromise: InternalFuture.InternalPromise<Unit>? = null

    class InternalQueueWriter<T>(private val wrapper: InternalQueue<T>) : QueueWriter<T> {
        val logger = KotlinLogging.logger {}
        override fun offer(value: T): Boolean {
            val emptyPromise = wrapper.emptyPromise
            if (emptyPromise != null) {
                emptyPromise.complete(value)
                wrapper.emptyPromise = null
                return true
            }
            else {
                return wrapper.queue.offer(value)
            }
        }

        suspend fun addAsync(value: T) {
            if (!offer(value)) {
                val future = InternalFuture<Unit>({
                    wrapper.queue.add(value)
                })
                wrapper.fullPromise = future.promise()
                future.awaitAsync()
            }
        }
    }

    class InternalQueueReader<T>(private val wrapper: InternalQueue<T>) : QueueReader<T> {
        fun isEmpty(): Boolean = wrapper.queue.isEmpty()

        fun isNotEmpty(): Boolean = wrapper.queue.isNotEmpty()

        override fun poll(): T? {
            val result = wrapper.queue.poll()
            if (result != null) {
                val fullPromise = wrapper.fullPromise
                if (fullPromise != null) {
                    fullPromise.complete(Unit)
                    wrapper.fullPromise = null
                }
            }
            return result
        }

        fun size(): Int = wrapper.queue.size

        fun pollAndAdd(value: T): T {
            // Using the raw queue.poll() here because poll() fulfills the fullPromise, which shouldn't happen here
            val result = wrapper.queue.poll()
            if (result != null) {
                // If the queue had another value, return it and append the new value to the end of the queue
                wrapper.queue.add(value)
                return result
            }
            else {
                // If the queue was empty, just return the current value again
                return value
            }
        }

        suspend fun awaitAsync(): T {
            val future = InternalFuture<T>()
            wrapper.emptyPromise = future.promise()
            return future.awaitAsync()
        }

        /*
           NOTE: a poll() followed by an awaitAsync is preferred when performance is important
           to avoid unnecessary suspending function calls
         */
        suspend fun nextAsync(): T {
            val result = poll()
            if (result != null) {
                return result
            }
            else {
                return awaitAsync()
            }
        }
    }
}
