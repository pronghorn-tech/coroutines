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

import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.ServiceCoroutineContext
import tech.pronghorn.coroutines.messages.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

interface CoroutinePromise<T> {
    fun isCancelled(): Boolean

    fun isDone(): Boolean

    fun complete(result: T)

    fun completeExceptionally(exception: Throwable)

    fun cancel(): Boolean
}

class CoroutineFuture<T>(private val onComplete: ((T) -> Unit)? = null) : Awaitable<T>() {
    private var result: T? = null
    private var exception: ExecutionException? = null
    private var state = FutureState.INITIALIZED
    private var waiter: Continuation<T>? = null

    companion object {
        private val cancelledException = CancellationException()
    }

    internal class ExternalPromise<T>(private val worker: CoroutineWorker,
                                      private val future: CoroutineFuture<T>) : CoroutinePromise<T> {
        private var state = FutureState.PROMISED

        override fun isCancelled(): Boolean = state == FutureState.CANCELLED

        override fun isDone(): Boolean = state != FutureState.PROMISED

        override fun complete(result: T) {
            if (state != FutureState.PROMISED) {
                throw IllegalStateException("Attempted to complete future in invalid state : $state")
            }
            state = FutureState.COMPLETED_SUCCESS
            worker.sendInterWorkerMessage(ExternalPromiseCompletionMessage(future, result))
        }

        override fun completeExceptionally(exception: Throwable) {
            if (state != FutureState.PROMISED) {
                throw IllegalStateException("Attempted to complete future in invalid state : $state")
            }
            state = FutureState.COMPLETED_EXCEPTION
            worker.sendInterWorkerMessage(ExternalPromiseExceptionMessage(future, exception))
        }

        override fun cancel(): Boolean {
            if (state != FutureState.PROMISED) {
                return false
            }
            else {
                state = FutureState.CANCELLED
                worker.sendInterWorkerMessage(ExternalPromiseCancelMessage(future))
                return true
            }
        }
    }

    internal class InternalPromise<T>(private val future: CoroutineFuture<T>) : CoroutinePromise<T> {
        override fun isCancelled(): Boolean = future.isCancelled()

        override fun isDone(): Boolean = future.isDone()

        override fun complete(result: T) {
            if (future.state != FutureState.PROMISED) {
                throw IllegalStateException("Attempted to complete future in invalid state : ${future.state}")
            }
            future.onComplete?.invoke(result)
            future.result = result
            future.state = FutureState.COMPLETED_SUCCESS
            future.wake(result)
        }

        override fun completeExceptionally(exception: Throwable) {
            future.exception = ExecutionException(exception)
            future.state = FutureState.COMPLETED_EXCEPTION
            future.wakeExceptionally(exception)
        }

        override fun cancel(): Boolean {
            if(future.isDone()){
                return false
            }

            future.state = FutureState.CANCELLED
            future.wakeExceptionally(cancelledException)
            return true
        }
    }

    fun externalPromise(worker: CoroutineWorker): CoroutinePromise<T> {
        if (state != FutureState.INITIALIZED) {
            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once, current state : $state")
        }
        state = FutureState.PROMISED
        return ExternalPromise(worker, this)
    }

    fun promise(): CoroutinePromise<T> {
        if (state != FutureState.INITIALIZED) {
            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once, current state : $state")
        }
        state = FutureState.PROMISED
        return InternalPromise(this)
    }

    internal fun externalComplete(result: T) {
        onComplete?.invoke(result)
        this.result = result
        state = FutureState.COMPLETED_SUCCESS
        wake(result)
    }

    internal fun externalCompleteExceptionally(exception: Throwable){
        val wrapped = ExecutionException(exception)
        this.exception = wrapped
        state = FutureState.COMPLETED_EXCEPTION
        wakeExceptionally(wrapped)
    }

    internal fun externalCancel(): Boolean {
        if (state != FutureState.INITIALIZED && state != FutureState.PROMISED) {
            return false
        }

        state = FutureState.CANCELLED
        wakeExceptionally(cancelledException)
        return true
    }

    private fun wakeExceptionally(throwable: Throwable) {
        val waiter = this.waiter
        if (waiter != null) {
            this.waiter = null
            val context = waiter.context
            if (context is ServiceCoroutineContext) {
                context.service.wakeExceptionally(throwable)
            }
            else {
                waiter.resumeWithException(throwable)
            }
        }
    }

    private fun wake(value: T) {
        val waiter = this.waiter
        if (waiter != null) {
            this.waiter = null
            val context = waiter.context
            if (context is ServiceCoroutineContext) {
                context.service.wake(value)
            }
            else {
                waiter.resume(value)
            }
        }
    }

    override fun poll(): T? = if (isDone()) getValue() else null

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
            if (context is ServiceCoroutineContext) {
                context.service.yield(continuation)
            }
            COROUTINE_SUSPENDED
        }
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
            else -> throw IllegalStateException("Cannot getValue from future in state : $state")
        }
    }
}
//
//class InternalFuture<T>(private val onComplete: ((T) -> Unit)? = null) : Awaitable<T>() {
//    private var result: T? = null
//    private var exception: ExecutionException? = null
//    private var state = FutureState.INITIALIZED
//    private var waiter: Continuation<T>? = null
//
//    companion object {
//        private val cancelledException = CancellationException()
//
////        fun <C> completed(value: C): InternalFuture<C> = InternalFuture(value)
//    }
//
////    private constructor(value: T) : this() {
////        this.result = value
////        this.state = FutureState.COMPLETED_SUCCESS
////    }
//
//    class InternalPromise<T>(private val future: InternalFuture<T>) {
//        fun isCancelled(): Boolean = future.isCancelled()
//
//        fun complete(result: T) {
//            if (future.state != FutureState.PROMISED) {
//                throw IllegalStateException("Attempted to complete future in invalid state : ${future.state}")
//            }
//            future.onComplete?.invoke(result)
//            future.result = result
//            future.state = FutureState.COMPLETED_SUCCESS
//            future.wake(result)
//        }
//
//        fun completeExceptionally(exception: Throwable) {
//            future.exception = ExecutionException(exception)
//            future.state = FutureState.COMPLETED_EXCEPTION
//            future.wakeExceptionally(exception)
//        }
//
//        fun cancel(): Boolean = future.cancel()
//    }
//
//    fun promise(): InternalPromise<T> {
//        if (state != FutureState.INITIALIZED) {
//            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once.")
//        }
//        state = FutureState.PROMISED
//        return InternalPromise(this)
//    }
//
//    private fun wakeExceptionally(throwable: Throwable) {
//        val waiter = this.waiter
//        if (waiter != null) {
//            this.waiter = null
//            val context = waiter.context
//            if(context is ServiceCoroutineContext) {
//                context.service.wakeExceptionally(throwable)
//            }
//            else {
//                waiter.resumeWithException(throwable)
//            }
//        }
//    }
//
//
//
//    private fun wake(value: T) {
//        val waiter = this.waiter
//        if (waiter != null) {
//            this.waiter = null
//            val context = waiter.context
//            if(context is ServiceCoroutineContext) {
//                context.service.wake(value)
//            }
//            else {
//                waiter.resume(value)
//            }
//        }
//    }
//
//    override fun poll(): T? = if(isDone()) getValue() else null
//
//    override suspend fun awaitAsync(): T {
//        if (isDone()) {
//            return getValue()
//        }
//        return suspendCoroutineOrReturn { continuation ->
//            if (waiter != null) {
//                throw IllegalStateException("Only one waiter is allowed.")
//            }
//            waiter = continuation
//            val context = continuation.context
//            if (context is ServiceCoroutineContext) {
//                context.service.yield(continuation)
//            }
//            COROUTINE_SUSPENDED
//        }
//    }
//
//    fun cancel(): Boolean {
//        if (state != FutureState.INITIALIZED && state != FutureState.PROMISED) {
//            return false
//        }
//
//        state = FutureState.CANCELLED
//        wakeExceptionally(cancelledException)
//        return true
//    }
//
//    fun isCancelled(): Boolean {
//        return state == FutureState.CANCELLED
//    }
//
//    fun isDone(): Boolean {
//        return state != FutureState.PROMISED && state != FutureState.INITIALIZED
//    }
//
//    private fun getValue(): T {
//        when (state) {
//            FutureState.COMPLETED_SUCCESS -> return result!!
//            FutureState.COMPLETED_EXCEPTION -> throw exception!!
//            FutureState.CANCELLED -> throw cancelledException
//            else -> throw IllegalStateException()
//        }
//    }
//}
