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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.awaitable.future.CoroutineFuture
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.Service
import tech.pronghorn.test.*
import java.util.ArrayDeque

class AwaitTests : PronghornTest() {
    // A service that awaits the provided futures and updates its worker's done variable
    class AwaitingService(override val worker: CoroutineWorker,
                          private val futures: List<CoroutineFuture<Int>>) : Service() {
        @Volatile
        var done = 0
            private set

        override suspend fun run() {
            when (futures.size) {
                1 -> done = await(futures.first())
                2 -> {
                    val (futureA, futureB) = futures
                    val (a, b) = await(futureA, futureB)
                    done = a + b
                }
                3 -> {
                    val (futureA, futureB, futureC) = futures
                    val (a, b, c) = await(futureA, futureB, futureC)
                    done = a + b + c
                }
                4 -> {
                    val (futureA, futureB, futureC, futureD) = futures
                    val (a, b, c, d) = await(futureA, futureB, futureC, futureD)
                    done = a + b + c + d
                }
                else -> throw Exception("Awaiting only supported for 1 to 4 awaiters.")
            }
        }
    }

    // Creates a worker with a service to await and a service to complete a variable number of internal futures
    fun variableAwait(futureCount: Int) {
        val worker = CoroutineWorker()
        val externalWorker = CoroutineWorker()
        val futures = List(futureCount, { CoroutineFuture<Int>() })
        val awaitingService = AwaitingService(worker, futures)
        worker.addService(awaitingService)
        worker.start()
        externalWorker.start()

        var totalValue = 0
        externalWorker.executeInWorker {
            val promises = ArrayDeque(futures.map { future -> future.externalPromise(/*worker*/) })
            var promise = promises.poll()
            while(promise != null){
                val value = random.nextInt(64)
                promise.complete(value)
                totalValue += value
                promise = promises.poll()
            }
        }

        try {
            eventually {
                assertEquals(totalValue, awaitingService.done)
            }
        }
        finally {
            worker.shutdown()
            externalWorker.shutdown()
        }
    }

    @RepeatedTest(heavyRepeatCount)
    fun await1Test() {
        variableAwait(1)
    }

    @RepeatedTest(heavyRepeatCount)
    fun await2Test() {
        variableAwait(2)
    }

    @RepeatedTest(heavyRepeatCount)
    fun await3Test() {
        variableAwait(3)
    }

    @RepeatedTest(heavyRepeatCount)
    fun await4Test() {
        variableAwait(4)
    }
}
