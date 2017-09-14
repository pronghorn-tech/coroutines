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

import java.time.Duration

enum class IntervalServiceContract {
    BestEffort,
    Precise,
    StrictPrecise
}

/**
 * A service that calls process() on an interval.
 *
 * @param interval time desired between calls to process(), actual time may be greater
 */
abstract class IntervalService(val interval: Duration,
                               val intervalServiceContract: IntervalServiceContract = IntervalServiceContract.BestEffort,
                               var warnOnOverload: Boolean = true) : InternalSleepableService() {
    private val durationAsMillis = interval.toMillis()
    private var lastRunTime = System.currentTimeMillis()

    var nextRunTime = lastRunTime + durationAsMillis
        private set

    override suspend fun run() {
        while(isRunning) {
            sleepAsync()
            val now = System.currentTimeMillis()
            process()
            when(intervalServiceContract) {
                IntervalServiceContract.BestEffort -> {
                    if(warnOnOverload && now > nextRunTime + durationAsMillis){
                        logger.warn { "Worker overloaded, IntervalService could not run during requested interval." }
                    }
                    lastRunTime = now
                    nextRunTime = lastRunTime + durationAsMillis
                }
                IntervalServiceContract.Precise -> {
                    lastRunTime = now
                    nextRunTime += durationAsMillis
                    if(nextRunTime < now) {
                        var skipped = 0
                        while (nextRunTime < now) {
                            skipped += 1
                            nextRunTime += durationAsMillis
                        }
                        if(warnOnOverload) {
                            logger.warn { "Worker overloaded, IntervalService could not run during interval. Skipped $skipped intervals." }
                        }
                    }
                }
                IntervalServiceContract.StrictPrecise -> {
                    lastRunTime = now
                    nextRunTime += durationAsMillis
                    if(warnOnOverload && nextRunTime < now){
                        logger.warn { "IntervalService process too longer to run than interval duration." }
                    }
                }
            }
        }
    }

    abstract fun process()
}
