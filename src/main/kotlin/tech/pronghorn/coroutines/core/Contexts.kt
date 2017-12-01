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

package tech.pronghorn.coroutines.core

import kotlin.coroutines.experimental.*

public sealed class PronghornCoroutineContext(key: CoroutineContext.Key<*>) : AbstractCoroutineContextElement(key) {
    private var wakeValue: Any? = null
    private var isWakeValueException = false

    @PublishedApi internal open fun onSuspend() = Unit

    internal open fun onResume() = Unit

    private fun validateWakeable() {
        if (wakeValue != null) {
            throw IllegalStateException("Unexpected overwrite of wake value.")
        }
    }

    public fun wakeExceptionally(continuation: Continuation<*>,
                                 throwable: Throwable) {
        if (DEBUG) { validateWakeable() }
        isWakeValueException = true
        wakeValue = throwable
        worker.resumeContinuation(continuation)
    }

    public fun <T> wake(continuation: Continuation<T>,
                        value: T) {
        if (DEBUG) { validateWakeable() }
        wakeValue = value
        worker.resumeContinuation(continuation)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> resume(continuation: Continuation<T>) {
        val wakeValue = this.wakeValue
        this.wakeValue = null
        if (isWakeValueException) {
            isWakeValueException = false
            continuation.resumeWithException(wakeValue as Throwable)
        }
        else {
            continuation.resume(wakeValue as T)
        }
    }

    public abstract val worker: CoroutineWorker
}

public class WorkerCoroutineContext(override val worker: CoroutineWorker) : PronghornCoroutineContext(WorkerCoroutineContext) {
    companion object Key : CoroutineContext.Key<WorkerCoroutineContext>
}

public class ServiceCoroutineContext(val service: Service) : PronghornCoroutineContext(ServiceCoroutineContext) {
    override val worker = service.worker

    override fun onSuspend() = service.internalOnSuspend()

    override fun onResume() = service.internalOnResume()

    companion object Key : CoroutineContext.Key<ServiceCoroutineContext>
}
