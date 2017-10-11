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

package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.ServiceCoroutineContext
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.launchServiceCoroutine
import tech.pronghorn.plugins.logging.LoggingPlugin
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

private val serviceIDs = AtomicLong(0)

@RestrictsSuspension
abstract class Service {
    protected val logger = LoggingPlugin.get(javaClass)
    protected val serviceID = serviceIDs.incrementAndGet()
    protected var isRunning = true
    abstract val worker: CoroutineWorker
    private var wakeValue: Any? = null

    var isQueued = false

    var continuation: Continuation<Any>? = null
        private set

    abstract suspend protected fun run()

    /**
     * Offers a hook for a function to be called each time this service suspends, useful for counters or stats tracking.
     * Called when suspending regardless of reason, whether due to shouldYield() or any async call.
     */
    open fun onSuspend() = Unit

    open fun onStart() = Unit

    open fun onShutdown() = Unit

    fun shutdown() {
        onShutdown()
        isRunning = false
    }

    fun start() {
        onStart()
        launchServiceCoroutine(ServiceCoroutineContext(this)) { runWrapper() }
    }

    private suspend fun runWrapper() {
        try {
            yieldAsync()
            run()
        }
        catch (ex: Exception) {
            ex.printStackTrace()
        }
        catch (ex: Error) {
            ex.printStackTrace()
            throw ex
        }
    }

    fun resume() {
        val wakeValue = this.wakeValue
        val continuation = this.continuation
        if (wakeValue == null || continuation == null) {
            throw IllegalStateException("Unexpected null wake value.")
        }
        else {
            this.wakeValue = null
            this.continuation = null
            continuation.resume(wakeValue)
        }
    }

    fun <T> wake(value: T) {
        if (wakeValue != null) {
            throw IllegalStateException("Unexpected overwrite of wake value.")
        }
        else if (continuation == null) {
            throw IllegalStateException("Unable to wake service without a continuation.")
        }
        wakeValue = value
        worker.offerReady(this)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> yield(continuation: Continuation<T>) {
        this.continuation = continuation as Continuation<Any>
        onSuspend()
    }

    protected suspend fun yieldAsync() {
        suspendCoroutineOrReturn { continuation: Continuation<Any> ->
            yield(continuation)
            wake(Unit)
            COROUTINE_SUSPENDED
        }
    }
}
