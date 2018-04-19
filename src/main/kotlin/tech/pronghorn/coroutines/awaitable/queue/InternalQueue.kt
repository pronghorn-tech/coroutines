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

package tech.pronghorn.coroutines.awaitable.queue

import tech.pronghorn.coroutines.core.PronghornCoroutineContext
import tech.pronghorn.coroutines.core.suspendCoroutine
import tech.pronghorn.plugins.internalQueue.InternalQueuePlugin
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

public class InternalQueue<T>(capacity: Int) {
    private val queue = InternalQueuePlugin.getBounded<T>(validateQueueCapacity(capacity))
    private val emptyWaiters = InternalQueuePlugin.getUnbounded<Continuation<T>>()
    private val fullWaiters = InternalQueuePlugin.getUnbounded<Continuation<Unit>>()

    public val reader = Reader(this)
    public val writer = Writer(this)

    public class Writer<T>(private val wrapper: InternalQueue<T>) {
        public fun offer(value: T): Boolean {
            val emptyWaiter = wrapper.emptyWaiters.poll()
            if (emptyWaiter != null) {
                (emptyWaiter.context as PronghornCoroutineContext).wake(emptyWaiter, value)
                return true
            }
            else {
                return wrapper.queue.offer(value)
            }
        }

        public suspend fun addAsync(value: T): Boolean {
            while(!offer(value)) {
                suspendCoroutine { continuation: Continuation<Unit> ->
                    wrapper.fullWaiters.offer(continuation)
                    COROUTINE_SUSPENDED
                }
            }
            return true
        }
    }

    public class Reader<T>(private val wrapper: InternalQueue<T>) {
        public fun isEmpty(): Boolean = wrapper.queue.isEmpty()

        public fun isNotEmpty(): Boolean = wrapper.queue.isNotEmpty()

        public fun size(): Int = wrapper.queue.size

        public fun peek(): T? = wrapper.queue.peek()

        public fun pollAndAdd(value: T): T {
            // Using the raw queue.poll() here because poll() fulfills a fullWaiter, which shouldn't happen here
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

        public fun poll(): T? {
            val result = wrapper.queue.poll()
            if (result != null) {
                val waiter = wrapper.fullWaiters.poll()
                if(waiter != null) {
                    (waiter.context as PronghornCoroutineContext).wake(waiter, Unit)
                }
            }
            return result
        }

        public suspend fun awaitAsync(): T {
            val result = poll()
            if (result != null) {
                return result
            }
            else {
                return suspendCoroutine { continuation: Continuation<T> ->
                    wrapper.emptyWaiters.offer(continuation)
                    COROUTINE_SUSPENDED
                }
            }
        }
    }
}
