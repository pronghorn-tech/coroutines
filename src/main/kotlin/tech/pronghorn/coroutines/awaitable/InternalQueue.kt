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

import tech.pronghorn.plugins.spscQueue.SpscQueuePlugin
import tech.pronghorn.util.isPowerOfTwo
import java.util.Queue

class InternalQueue<T>(private val queue: Queue<T>) {
    val capacity = queue.size

    constructor(capacity: Int) : this(SpscQueuePlugin.getBounded<T>(capacity)) {
        if (capacity < 4) {
            throw Exception("Queue size must be at least four.")
        }
        else if (!isPowerOfTwo(capacity)) {
            throw Exception("Queue sizes must be powers of two.")
        }
    }

    val queueReader = InternalQueueReader(this)
    val queueWriter = InternalQueueWriter(this)

    private val emptyWaiters = SpscQueuePlugin.getUnbounded<InternalFuture.InternalPromise<T>>()
    private val fullWaiters = SpscQueuePlugin.getUnbounded<InternalFuture.InternalPromise<Unit>>()

    class InternalQueueWriter<T>(private val wrapper: InternalQueue<T>) : QueueWriter<T> {
        override fun offer(value: T): Boolean {
            val emptyPromise = wrapper.emptyWaiters.poll()
            if (emptyPromise != null) {
                emptyPromise.complete(value)
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
                wrapper.fullWaiters.add(future.promise())
                await(future)
            }
        }
    }

    class InternalQueueReader<T>(private val wrapper: InternalQueue<T>) : QueueReader<T> {
        fun isEmpty(): Boolean = wrapper.queue.isEmpty()

        fun isNotEmpty(): Boolean = wrapper.queue.isNotEmpty()

        override fun poll(): T? {
            val result = wrapper.queue.poll()
            if (result != null) {
                wrapper.fullWaiters.poll()?.complete(Unit)
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

        override suspend fun awaitAsync(): T {
            val result = poll()
            if (result != null) {
                return result
            }
            else {
                val future = InternalFuture<T>()
                wrapper.emptyWaiters.add(future.promise())
                return await(future)
            }
        }
    }
}
