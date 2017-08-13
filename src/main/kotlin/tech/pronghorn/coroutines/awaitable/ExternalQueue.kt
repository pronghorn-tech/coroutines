package tech.pronghorn.coroutines.awaitable

import tech.pronghorn.coroutines.service.QueueService
import tech.pronghorn.plugins.spscQueue.SpscQueuePlugin
import tech.pronghorn.util.isPowerOfTwo
import java.util.concurrent.locks.ReentrantLock

class ExternalQueue<T>(capacity: Int,
                       val service: QueueService<T>) {
    init {
        if (capacity < 4) {
            throw Exception("Queue size must be at least four.")
        } else if (!isPowerOfTwo(capacity)) {
            throw Exception("Queue sizes must be powers of two.")
        }
    }

    private val queue = SpscQueuePlugin.get<T>(capacity)

    val queueReader = ExternalQueueReader<T>(this)
    val queueWriter = ExternalQueueWriter<T>(this)

    private var emptyPromise: InternalFuture.InternalPromise<T>? = null
    private val lock = ReentrantLock()

    class ExternalQueueWriter<in T>(private val wrapper: ExternalQueue<T>) : QueueWriter<T> {
        override fun offer(value: T): Boolean {
            wrapper.lock.lock()
            try {
                val emptyPromise = wrapper.emptyPromise
                if (emptyPromise != null) {
                    wrapper.service.worker.crossThreadCompletePromise(emptyPromise, value)
                    wrapper.emptyPromise = null
                    return true
                } else {
                    return wrapper.queue.offer(value)
                }
            } finally {
                wrapper.lock.unlock()
            }
        }
    }

    class ExternalQueueReader<out T>(private val wrapper: ExternalQueue<T>) : QueueReader<T> {
        override fun poll(): T? = wrapper.queue.poll()

        suspend fun nextAsync(): T {
            val result = poll()
            if (result != null) {
                return result
            } else {
                val future = InternalFuture<T>()
                wrapper.lock.lock()
                try {
                    val check = poll()
                    if (check != null) {
                        return check
                    }

                    wrapper.emptyPromise = future.promise()
                } finally {
                    wrapper.lock.unlock()
                }

                return future.awaitAsync()
            }
        }
    }
}
