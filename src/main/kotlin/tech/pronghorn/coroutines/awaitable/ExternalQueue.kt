/*
 * Copyright 2017 Pronghorn Technology LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        }
        else if (!isPowerOfTwo(capacity)) {
            throw Exception("Queue sizes must be powers of two.")
        }
    }

    private val queue = SpscQueuePlugin.getBounded<T>(capacity)
    private val emptyWaiters = SpscQueuePlugin.getUnbounded<InternalFuture.InternalPromise<T>>()
    private val lock = ReentrantLock()

    val queueReader = ExternalQueueReader(this)
    val queueWriter = ExternalQueueWriter(this)

    class ExternalQueueWriter<in T>(private val wrapper: ExternalQueue<T>) : QueueWriter<T> {
        override fun offer(value: T): Boolean {
            wrapper.lock.lock()
            try {
                val waiter = wrapper.emptyWaiters.poll()
                if (waiter != null) {
                    wrapper.service.worker.crossThreadCompletePromise(waiter, value)
                    return true
                }
                else {
                    return wrapper.queue.offer(value)
                }
            }
            finally {
                wrapper.lock.unlock()
            }
        }
    }

    class ExternalQueueReader<out T>(private val wrapper: ExternalQueue<T>) : QueueReader<T> {
        override fun poll(): T? = wrapper.queue.poll()

        override suspend fun awaitAsync(): T {
            val result = poll()
            if (result != null) {
                return result
            }
            else {
                val future = InternalFuture<T>()
                wrapper.lock.lock()
                try {
                    val check = poll()
                    if (check != null) {
                        return check
                    }

                    wrapper.emptyWaiters.add(future.promise())
                }
                finally {
                    wrapper.lock.unlock()
                }

                return await(future)
            }
        }
    }
}
