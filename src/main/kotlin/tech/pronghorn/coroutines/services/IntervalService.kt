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

/**
 * @param BestEffort each process() call takes place as soon as possible after a Duration past the last run
 * @param Precise attempts to call process() after a Duration from a single starting point, skipping calls if the
 * interval cannot be executed within the interval between Durations
 * @param StrictPrecise attempts to call process() after a Duration from a single starting point, process() is called
 * once for each passed interval even if multiple have passed.
 */
public enum class IntervalServiceContract {
    BestEffort,
    Precise,
    StrictPrecise
}

/**
 * A service that calls process() on an interval.
 *
 * @param interval time desired between calls to process(), actual time may be greater
 * @param intervalServiceContract what sort of timing guarantees are provided
 */
public abstract class IntervalService(protected val interval: Duration,
                                      protected val intervalServiceContract: IntervalServiceContract = IntervalServiceContract.BestEffort): TimedService() {
    private var continuation: Continuation<Unit>? = null
    private val durationAsNanos = interval.toNanos()
    private var lastRunTime = System.nanoTime()
    private var nextRunTime = lastRunTime + durationAsNanos

    final override fun getNextRunTime(): Long = nextRunTime

    open fun onOverload() = Unit

    public suspend fun sleepAsync() {
        suspendCoroutine { continuation: Continuation<Unit> ->
            this.continuation = continuation
            COROUTINE_SUSPENDED
        }
    }

    final override fun wake(): Boolean {
        val continuation = this.continuation
        if(continuation != null){
            this.continuation = null
            (continuation.context as PronghornCoroutineContext).wake(continuation, Unit)
            return true
        }
        return false
    }

    final override suspend fun run() {
        while (isRunning()) {
            sleepAsync()
            val now = System.nanoTime()
            process()
            when (intervalServiceContract) {
                IntervalServiceContract.BestEffort -> {
                    if ((nextRunTime + durationAsNanos) - now < 0) {
                        onOverload()
                    }
                    lastRunTime = now
                    nextRunTime = lastRunTime + durationAsNanos
                }
                IntervalServiceContract.Precise -> {
                    lastRunTime = now
                    nextRunTime += durationAsNanos
                    if (nextRunTime - now < 0) {
                        var skipped = 0
                        while (nextRunTime - now < 0) {
                            skipped += 1
                            nextRunTime += durationAsNanos
                        }
                        onOverload()
                    }
                }
                IntervalServiceContract.StrictPrecise -> {
                    lastRunTime = now
                    nextRunTime += durationAsNanos
                    if (nextRunTime - now < 0) {
                        onOverload()
                    }
                }
            }
        }
    }

    protected abstract fun process()
}
