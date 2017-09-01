package tech.pronghorn.coroutines.awaitable

import mu.KotlinLogging
import tech.pronghorn.plugins.mpmcQueue.MpmcQueuePlugin
import tech.pronghorn.util.isPowerOfTwo
import tech.pronghorn.util.roundToPowerOfTwo
import java.util.*

class SharedQueue<T>(private val queue: Queue<T>) {
    val capacity = queue.size

    constructor(capacity: Int) : this(MpmcQueuePlugin.get<T>(capacity)) {
        if (capacity < 4) {
            throw Exception("Queue size must be at least four.")
        }
        else if (!isPowerOfTwo(capacity)) {
            throw Exception("Queue sizes must be powers of two.")
        }
    }

    val queueReader = SharedQueueReader(this)
    val queueWriter = SharedQueueWriter(this)

    private var emptyPromise: CoroutineFuture.CoroutinePromise<T>? = null
    private var fullPromise: CoroutineFuture.CoroutinePromise<Unit>? = null

    class SharedQueueWriter<T>(private val wrapper: SharedQueue<T>) : QueueWriter<T> {
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

//        suspend fun addAsync(value: T) {
//            if (!offer(value)) {
//                val future = CoroutineFuture<Unit>({
//                    wrapper.queue.add(value)
//                })
//                wrapper.fullPromise = future.promise()
//                future.awaitAsync()
//            }
//        }
    }

    class SharedQueueReader<T>(private val wrapper: SharedQueue<T>) : QueueReader<T> {
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

        override suspend fun awaitAsync(): T {
            TODO()
        }

//        suspend fun awaitAsync(): T {
//            val future = InternalFuture<T>()
//            wrapper.emptyPromise = future.promise()
//            return future.awaitAsync()
//        }
//
//        /*
//           NOTE: a poll() followed by an awaitAsync is preferred when performance is important
//           to avoid unnecessary suspending function calls
//         */
//        suspend fun nextAsync(): T {
//            val result = poll()
//            if (result != null) {
//                return result
//            }
//            else {
//                return awaitAsync()
//            }
//        }
    }
}
