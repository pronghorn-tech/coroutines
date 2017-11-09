/*
 * Copyright 2018 Pronghorn Technology LLC
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

package tech.pronghorn.coroutines.awaitable.lock

import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.coroutines.awaitable.future.CoroutineFuture
import tech.pronghorn.coroutines.awaitable.future.CoroutinePromise
import tech.pronghorn.plugins.mpscQueue.MpscQueuePlugin
import java.util.concurrent.atomic.AtomicBoolean

public class SuspendableLock {
    private val queue = MpscQueuePlugin.getUnbounded<CoroutinePromise<Unit>>()
    @PublishedApi internal val tracker = AtomicBoolean(false)

    @PublishedApi internal fun wake() {
        var toComplete = queue.poll()
        while(toComplete != null){
            if(toComplete.complete(Unit)){
                return
            }
            toComplete = queue.poll()
        }
    }

    @PublishedApi internal inline fun <T> executeLock(block: () -> T): T {
        val value = block()
        tracker.set(false)
        wake()
        return value
    }

    public inline fun <T> tryLock(block: () -> T): T? {
        if(tracker.compareAndSet(false, true)){
            return executeLock(block)
        }
        else {
            return null
        }
    }

    public tailrec suspend fun <T> withLock(block: () -> T): T {
        if (tracker.compareAndSet(false, true)) {
            return executeLock(block)
        }
        else {
            val future = CoroutineFuture<Unit>()
            val promise = future.externalPromise()
            queue.offer(promise)
            if (tracker.compareAndSet(false, true)) {
                promise.cancel()
                return executeLock(block)
            }
            else {
                await(future)
                return withLock(block)
            }
        }
    }
}
