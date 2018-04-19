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
 *//*


package tech.pronghorn.coroutines.awaitable.future.primitive

import tech.pronghorn.coroutines.awaitable.future.FutureState
import tech.pronghorn.coroutines.core.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

public class CoroutineFutureLong(private val onComplete: ((Long) -> Unit)? = null) {
    private var result: Long = 0
    private var exception: ExecutionException? = null
    private var state = FutureState.INITIALIZED
    private var waiter: Continuation<Long>? = null

    companion object {
        private val cancelledException = CancellationException()

        public fun completed(value: Long): CoroutineFutureLong = CoroutineFutureLong(value)
    }

    internal constructor(value: Long) : this() {
        this.result = value
        this.state = FutureState.COMPLETED_SUCCESS
    }

    public fun externalPromise(): CoroutinePromiseLong {
        val worker = ThreadLocalWorker.get()
        if (state != FutureState.INITIALIZED) {
            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once, current state : $state")
        }
        state = FutureState.PROMISED
        return ExternalPromiseLong(worker, this)
    }

    public fun promise(): CoroutinePromiseLong {
        if (state != FutureState.INITIALIZED) {
            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once, current state : $state")
        }
        state = FutureState.PROMISED
        return InternalPromiseLong(this)
    }

    public fun poll(): Long? = if (isDone()) get() else null

    public suspend fun awaitAsync(): Long {
        if(isDone()) {
            return get()
        }

        return suspendCoroutine { continuation ->
            waiter = continuation
            COROUTINE_SUSPENDED
        }
    }

    public fun isCancelled(): Boolean {
        return state == FutureState.CANCELLED
    }

    public fun isDone(): Boolean {
        return state != FutureState.PROMISED && state != FutureState.INITIALIZED
    }

    internal fun completeFromWorker(result: Long) {
        if (isDone()) {
            throw IllegalStateException("Attempted to complete future in invalid state : $state")
        }
        onComplete?.invoke(result)
        this.result = result
        state = FutureState.COMPLETED_SUCCESS
        wake(result)
    }

    internal fun completeExceptionallyFromWorker(exception: Throwable){
        if (isDone()) {
            throw IllegalStateException("Attempted to complete future in invalid state : $state")
        }
        val wrapped = ExecutionException(exception)
        this.exception = wrapped
        state = FutureState.COMPLETED_EXCEPTION
        wakeExceptionally(wrapped)
    }

    internal fun cancelFromWorker() {
        if (isDone()) {
            throw IllegalStateException("Attempted to cancel future in invalid state : $state")
        }
        state = FutureState.CANCELLED
        wakeExceptionally(cancelledException)
    }

    @Suppress("UNCHECKED_CAST")
    private fun wakeExceptionally(throwable: Throwable) {
        val waiter = this.waiter ?: return
        val context = waiter.context as PronghornCoroutineContext
        context.wakeExceptionally(waiter as Continuation<Any>, throwable)
    }

    @Suppress("UNCHECKED_CAST")
    private fun wake(value: Long) {
        val waiter = this.waiter ?: return
        val context = waiter.context as PronghornCoroutineContext
        context.wake(waiter, value)
    }

    public fun get(): Long {
        when (state) {
            FutureState.COMPLETED_SUCCESS -> return result
            FutureState.COMPLETED_EXCEPTION -> throw exception!!
            FutureState.CANCELLED -> throw cancelledException
            else -> throw IllegalStateException("Cannot get from future in state : $state")
        }
    }
}
*/
