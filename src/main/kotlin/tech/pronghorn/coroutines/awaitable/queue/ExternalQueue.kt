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

import tech.pronghorn.coroutines.awaitable.Awaitable
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.coroutines.awaitable.future.CoroutineFuture
import tech.pronghorn.coroutines.awaitable.future.CoroutinePromise
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.plugins.mpscQueue.MpscQueuePlugin
import tech.pronghorn.plugins.spmcQueue.SpmcQueuePlugin

public class ExternalQueue<T>(capacity: Int,
                              public val worker: CoroutineWorker) {
    private val queue = MpscQueuePlugin.getBounded<T>(validateQueueCapacity(capacity))
    private val emptyWaiters = SpmcQueuePlugin.getUnbounded<CoroutinePromise<T>>()
    private val fullWaiters = MpscQueuePlugin.getUnbounded<CoroutinePromise<Unit>>()

    val reader = Reader(this)
    val writer = Writer(this)

    public class Writer<in T>(private val wrapper: ExternalQueue<T>) {
        public fun offer(value: T): Boolean {
            var waiter = wrapper.emptyWaiters.poll()
            while(waiter != null){
                if(waiter.complete(value)){
                    return true
                }
                waiter = wrapper.emptyWaiters.poll()
            }

            return wrapper.queue.offer(value)
        }

        public tailrec suspend fun addAsync(value: T): Boolean {
            if(offer(value)){
                return true
            }

            val future = CoroutineFuture<Unit>()
            val promise = future.externalPromise()
            wrapper.fullWaiters.add(promise)
            if(offer(value)){
                promise.cancel()
                return true
            }
            await(future)
            return addAsync(value)
        }
    }

    public class Reader<T>(private val wrapper: ExternalQueue<T>) : Awaitable<T>() {
        override fun poll(): T? {
            val result = wrapper.queue.poll()
            if(result != null){
                var promise = wrapper.fullWaiters.poll()
                while(promise != null){
                    if(promise.complete(Unit)){
                        return result
                    }
                    promise = wrapper.fullWaiters.poll()
                }
            }
            return result
        }

        override suspend fun awaitAsync(): T {
            val result = poll()
            if (result != null) {
                return result
            }
            val future = CoroutineFuture<T>()
            val promise = future.externalPromise()
            wrapper.emptyWaiters.offer(promise)
            if(wrapper.queue.peek() != null && promise.cancel()){
                return poll()!!
            }
            else {
                return await(future)
            }
        }
    }
}
