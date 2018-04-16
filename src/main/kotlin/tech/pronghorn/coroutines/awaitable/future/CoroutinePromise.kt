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

package tech.pronghorn.coroutines.awaitable.future

import tech.pronghorn.coroutines.core.CoroutineWorker
import java.util.concurrent.atomic.AtomicReference

public sealed class CoroutinePromise<T> {
    public abstract fun isCancelled(): Boolean

    public abstract fun isDone(): Boolean

    public abstract fun complete(result: T): Boolean

    public abstract fun completeExceptionally(throwable: Throwable): Boolean

    public abstract fun cancel(): Boolean
}

internal class ExternalPromise<T>(val worker: CoroutineWorker,
                                  private val future: CoroutineFuture<T>) : CoroutinePromise<T>() {
    private val state = AtomicReference<FutureState>(FutureState.PROMISED)

    override fun isCancelled(): Boolean = state.get() == FutureState.CANCELLED

    override fun isDone(): Boolean = state.get() != FutureState.PROMISED

    override fun complete(result: T): Boolean {
        if (!state.compareAndSet(FutureState.PROMISED, FutureState.COMPLETED_SUCCESS)){
            return false
        }

        worker.executeInWorker {
            future.completeFromWorker(result)
        }
        return true
    }

    override fun completeExceptionally(throwable: Throwable): Boolean {
        if (!state.compareAndSet(FutureState.PROMISED, FutureState.COMPLETED_EXCEPTION)){
            return false
        }

        worker.executeInWorker {
            future.completeExceptionallyFromWorker(throwable)
        }
        return true
    }

    override fun cancel(): Boolean {
        if (!state.compareAndSet(FutureState.PROMISED, FutureState.CANCELLED)){
            return false}

        worker.executeInWorker {
            future.cancelFromWorker()
        }
        return true
    }
}

internal class InternalPromise<T>(private val future: CoroutineFuture<T>) : CoroutinePromise<T>() {
    override fun isCancelled(): Boolean = future.isCancelled()

    override fun isDone(): Boolean = future.isDone()

    override fun complete(result: T): Boolean {
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
