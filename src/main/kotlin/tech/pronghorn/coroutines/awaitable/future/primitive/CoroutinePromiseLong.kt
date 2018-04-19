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
 *//*


package tech.pronghorn.coroutines.awaitable.future.primitive

import tech.pronghorn.coroutines.awaitable.future.FutureState
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.RunnableInWorker
import java.util.concurrent.atomic.AtomicInteger

public sealed class CoroutinePromiseLong {
    public abstract fun isCancelled(): Boolean

    public abstract fun isDone(): Boolean

    public abstract fun complete(result: Long): Boolean

    public abstract fun completeExceptionally(throwable: Throwable): Boolean

    public abstract fun cancel(): Boolean
}

internal class ExternalPromiseLong(val worker: CoroutineWorker,
                                   private val future: CoroutineFutureLong) : CoroutinePromiseLong(), RunnableInWorker {
    private val state = AtomicInteger(FutureState.PROMISED.ordinal)
    private var finalState: Int = 0
    private var result: Long = 0L
    private var exceptionalResult: Throwable? = null

    override fun isCancelled(): Boolean = state.get() == FutureState.CANCELLED.ordinal

    override fun isDone(): Boolean = state.get() != FutureState.PROMISED.ordinal

    override fun runInWorker() {
        when(finalState) {
            FutureState.COMPLETED_SUCCESS.ordinal -> future.completeFromWorker(result)
            FutureState.COMPLETED_EXCEPTION.ordinal -> future.completeExceptionallyFromWorker(exceptionalResult!!)
            FutureState.CANCELLED.ordinal -> future.cancelFromWorker()
            else -> IllegalStateException("Unexpected future state at resolution")
        }
    }

    override fun complete(result: Long): Boolean {
        if (!state.compareAndSet(FutureState.PROMISED.ordinal, FutureState.COMPLETED_SUCCESS.ordinal)){
            return false
        }

        finalState = FutureState.COMPLETED_SUCCESS.ordinal
        this.result = result

        worker.executeInWorker(this)
        return true
    }

    override fun completeExceptionally(throwable: Throwable): Boolean {
        if (!state.compareAndSet(FutureState.PROMISED.ordinal, FutureState.COMPLETED_EXCEPTION.ordinal)){
            return false
        }

        finalState = FutureState.COMPLETED_EXCEPTION.ordinal
        this.exceptionalResult = throwable

        worker.executeInWorker(this)
        return true
    }

    override fun cancel(): Boolean {
        if (!state.compareAndSet(FutureState.PROMISED.ordinal, FutureState.CANCELLED.ordinal)){
            return false
        }

        finalState = FutureState.CANCELLED.ordinal

        worker.executeInWorker(this)
        return true
    }
}

internal class InternalPromiseLong(private val future: CoroutineFutureLong) : CoroutinePromiseLong() {
    override fun isCancelled(): Boolean = future.isCancelled()

    override fun isDone(): Boolean = future.isDone()

    override fun complete(result: Long): Boolean {
        if(future.isDone()){
            return false
        }
        future.completeFromWorker(result)
        return true
    }

    override fun completeExceptionally(throwable: Throwable): Boolean {
        if(future.isDone()){
            return false
        }
        future.completeExceptionallyFromWorker(throwable)
        return true
    }

    override fun cancel(): Boolean {
        if(future.isDone()){
            return false
        }
        future.cancelFromWorker()
        return true
    }
}
*/
