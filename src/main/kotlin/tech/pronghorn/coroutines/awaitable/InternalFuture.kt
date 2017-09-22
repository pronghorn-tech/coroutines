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

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

internal enum class FutureState {
    INITIALIZED,
    PROMISED,
    COMPLETED_SUCCESS,
    COMPLETED_EXCEPTION,
    CANCELLED
}

interface Awaitable<out T> {
    suspend fun awaitAsync(): T
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class InternalFuture<T>(private val onComplete: ((T) -> Unit)? = null) : Awaitable<T> {
    private var result: T? = null
    private var exception: ExecutionException? = null
    private var state = FutureState.INITIALIZED
    private var waiter: Continuation<T>? = null

    companion object {
        private val cancelledException = CancellationException()
    }

    class InternalPromise<T>(private val future: InternalFuture<T>) {
        fun isCancelled(): Boolean = future.isCancelled()

        fun complete(result: T) {
            if (future.state != FutureState.PROMISED) {
                throw IllegalStateException("Attempted to complete future in invalid state : ${future.state}")
            }
            future.onComplete?.invoke(result)
            future.result = result
            future.state = FutureState.COMPLETED_SUCCESS
            future.wake(result)
        }

        fun completeExceptionally(exception: Throwable) {
            future.exception = ExecutionException(exception)
            future.state = FutureState.COMPLETED_EXCEPTION
            future.wakeExceptionally(exception)
        }
    }

    fun promise(): InternalPromise<T> {
        if (state != FutureState.INITIALIZED) {
            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once.")
        }
        state = FutureState.PROMISED
        return InternalPromise(this)
    }

    private fun wakeExceptionally(exception: Throwable) {
        val waiter = this.waiter
        if (waiter != null) {
            this.waiter = null
            val context = waiter.context
            when (context) {
                is ServiceCoroutineContext -> context.service.wake(exception)
                is ServiceManagedCoroutineContext -> {
                    waiter.resumeWithException(exception)
                }
                is EmptyCoroutineContext -> waiter.resumeWithException(exception)
                else -> {
                    throw Error("Can't wake context $context")
                }
            }
        }
    }

    private fun wake(value: T) {
        val waiter = this.waiter
        if (waiter != null) {
            this.waiter = null
            val context = waiter.context
            when (context) {
                is ServiceCoroutineContext -> context.service.wake(value)
                is ServiceManagedCoroutineContext -> {
                    waiter.resume(value)
                }
                is EmptyCoroutineContext -> waiter.resume(value)
                else -> {
                    throw Error("Can't wake context $context")
                }
            }
        }
    }

    override suspend fun awaitAsync(): T {
        if (isDone()) {
            return getValue()
        }
        return suspendCoroutineOrReturn { continuation ->
            if (waiter != null) {
                throw IllegalStateException("Only one waiter is allowed.")
            }
            waiter = continuation
            val context = continuation.context
            when (context) {
                is ServiceCoroutineContext -> {
                    context.service.yield(continuation)
                }
                is ServiceManagedCoroutineContext -> {
                    // no-op for the moment
                }
                else -> {
                    throw Exception("Illegal context type for awaiting a future.")
                }
            }
            COROUTINE_SUSPENDED
        }
    }

    fun cancel(): Boolean {
        if (state != FutureState.INITIALIZED && state != FutureState.PROMISED) {
            return false
        }

        state = FutureState.CANCELLED
        wakeExceptionally(cancelledException)
        return true
    }

    fun isCancelled(): Boolean {
        return state == FutureState.CANCELLED
    }

    fun isDone(): Boolean {
        return state != FutureState.PROMISED && state != FutureState.INITIALIZED
    }

    private fun getValue(): T {
        when (state) {
            FutureState.COMPLETED_SUCCESS -> return result!!
            FutureState.COMPLETED_EXCEPTION -> throw exception!!
            FutureState.CANCELLED -> throw cancelledException
            else -> throw IllegalStateException()
        }
    }
}
