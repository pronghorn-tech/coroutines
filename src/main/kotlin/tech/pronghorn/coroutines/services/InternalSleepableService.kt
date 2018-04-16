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

package tech.pronghorn.coroutines.services

import tech.pronghorn.coroutines.core.PronghornCoroutineContext
import tech.pronghorn.coroutines.core.suspendCoroutine
import java.time.Duration
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

public abstract class InternalSleepableService : TimedService() {
    private var continuation: Continuation<Unit>? = null
    private var nextRunTime: Long = NO_NEXT_RUN_TIME

    override fun getNextRunTime(): Long = nextRunTime

    public suspend fun sleepNanosAsync(nanos: Long) {
        nextRunTime = System.nanoTime() + nanos
        suspendCoroutine { continuation: Continuation<Unit> ->
            this.continuation = continuation
            COROUTINE_SUSPENDED
        }
    }

    public suspend fun sleepDurationAsync(duration: Duration) = sleepNanosAsync(duration.toNanos())

    public suspend fun sleepAsync() {
        suspendCoroutine { continuation: Continuation<Unit> ->
            this.continuation = continuation
            COROUTINE_SUSPENDED
        }
    }

    final override fun wake(): Boolean {
        val continuation = this.continuation
        if (continuation != null) {
            nextRunTime = NO_NEXT_RUN_TIME
            this.continuation = null
            (continuation.context as PronghornCoroutineContext).wake(continuation, Unit)
            return true
        }
        return false
    }
}
