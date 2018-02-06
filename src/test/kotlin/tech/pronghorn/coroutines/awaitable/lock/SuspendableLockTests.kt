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

package tech.pronghorn.coroutines.awaitable.lock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.Service
import tech.pronghorn.test.*

class SuspendableLockTests : PronghornTest() {
    class Counter {
        var count = 0
    }

    class LockingService(override val worker: CoroutineWorker,
                         val lock: SuspendableLock,
                         val counter: Counter,
                         val workSize: Int) : Service() {
        var localCount = 0
        fun increment() {
            localCount += 1
            counter.count += 1
        }

        suspend override fun run() {
            var x = 0
            while (x < workSize) {
                lock.tryLock { increment() } ?: lock.withLock { increment() }
                if (x % 1000 == 0) {
                    yieldAsync()
                }
                x += 1
            }
        }
    }

    class LockingWorker(lock: SuspendableLock,
                        counter: Counter,
                        workSize: Int) : CoroutineWorker() {
        val waiterService = LockingService(this, lock, counter, workSize)
        override val initialServices = listOf(waiterService)
    }

    // Ensures suspendable locks lock successfully for thread safety
    @RepeatedTest(lightRepeatCount)
    fun lockSafetyTest() {
        val lock = SuspendableLock()
        val counter = Counter()
        val workerCount = 2 + random.nextInt(16)
        val workSize = 10000 + random.nextInt(100000)
        val totalWork = workSize * workerCount
        val workers = (1..workerCount).map { LockingWorker(lock, counter, workSize) }
        try {
            val pre = System.currentTimeMillis()
            workers.forEach(CoroutineWorker::start)
            eventually {
                assertEquals(workerCount * workSize, counter.count)
            }
            val post = System.currentTimeMillis()
            logger.info { "Took ${post - pre}ms for $totalWork, ${(totalWork / Math.max(1, (post - pre))) / 1000.0} million per second" }
        }
        catch(ex: AssertionError){
            println(ex)
            throw ex
        }
        finally {
            workers.forEach(CoroutineWorker::shutdown)
        }
    }
}
