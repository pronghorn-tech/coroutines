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

import tech.pronghorn.plugins.logging.LoggingPlugin
import tech.pronghorn.util.stackTraceToString
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*
import kotlin.coroutines.experimental.startCoroutine

private val serviceIDs = AtomicLong(0)

internal enum class LifecycleState {
    Initialized,
    Running,
    Shutdown
}

@RestrictsSuspension
public abstract class Service: Lifecycle() {
    protected val logger = LoggingPlugin.get(javaClass)
    protected val serviceID = serviceIDs.incrementAndGet()
    public abstract val worker: CoroutineWorker
    public var suspendLocation: Array<StackTraceElement>? = null

    protected abstract suspend fun run()

    /**
     * Offers a hook for a function to be called each time this service suspends, useful for counters or stats tracking.
     * Called when suspending regardless of reason, whether due to shouldYield() or any async call.
     */
    protected open fun onSuspend() = Unit

    protected open fun onResume() = Unit

    internal fun internalOnSuspend() = onSuspend()

    internal fun internalOnResume() = onResume()

    internal fun start() = lifecycleStart()

    internal fun shutdown() = lifecycleShutdown()

    final override fun onLifecycleShutdown() = Unit

    final override fun onLifecycleStart() {
        launchServiceCoroutine { runWrapper() }
    }

    private fun <T> launchServiceCoroutine(block: suspend () -> T) = block.startCoroutine(PronghornCoroutine(ServiceCoroutineContext(this)))

    private suspend fun runWrapper() {
        try {
            yieldAsync()
            run()
        }
        catch (ex: Exception) {
            logger.error { ex.stackTraceToString() }
        }
        catch (ex: Error) {
            ex.printStackTrace()
            throw ex
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    protected suspend inline fun yieldAsync() {
        suspendCoroutine { continuation: Continuation<Unit> ->
            (continuation.context as PronghornCoroutineContext).wake(continuation, Unit)
            COROUTINE_SUSPENDED
        }
    }
}
