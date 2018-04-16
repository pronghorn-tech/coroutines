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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.Service
import tech.pronghorn.coroutines.services.InternalQueueService
import tech.pronghorn.test.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

class InternalPromiseTests : PronghornTest() {
    class InternalFutureWaiterService(override val worker: CoroutineWorker) : InternalQueueService<CoroutineFuture<Int>>() {
        var total = 0
        var cancelled = 0
        var exceptions = 0

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        suspend override fun process(future: CoroutineFuture<Int>): Boolean {
            try {
                val value = await(future)
                total += value
            }
            catch (ex: CancellationException) {
                cancelled += 1
            }
            catch (ex: ExecutionException) {
                exceptions += 1
            }
            return true
        }
    }

    abstract class FinishableService : Service() {
        @Volatile
        var finished = false
    }

    class WaiterWorker : CoroutineWorker() {
        val waiterService = InternalFutureWaiterService(this)
        val serviceWriter = waiterService.getQueueWriter()
        override val initialServices = listOf(waiterService)
    }

    val futureCount = 16

    // Completes each promise before the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun preCompleteTest() {
        val worker = WaiterWorker()

        val testService = object : FinishableService() {
            override val worker = worker

            override suspend fun run() {
                var x = 0
                var expected = 0
                while (x < futureCount) {
                    val future = CoroutineFuture<Int>()
                    val promise = future.promise()
                    promise.complete(x)
                    worker.serviceWriter.offer(future)
                    assertTrue(promise.isDone())
                    yieldAsync()
                    expected += x
                    assertEquals(expected, worker.waiterService.total)
                    assertTrue(future.isDone())
                    x += 1
                }
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    fun validateFinishes(worker: CoroutineWorker) {
        worker.start()

        try {
            eventually {
                assertTrue(worker.getService<FinishableService>()!!.finished)
            }
        }
        finally {
            worker.shutdown()
        }
    }

    // Completes each promise after the services awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun postCompleteTest() {
        val worker = WaiterWorker()
        val testService = object : FinishableService() {
            override val worker = worker
            override suspend fun run() {
                var x = 0
                var expected = 0
                while (x < futureCount) {
                    val future = CoroutineFuture<Int>()
                    val promise = future.promise()
                    worker.serviceWriter.offer(future)
                    promise.complete(x)
                    assertTrue(promise.isDone())
                    yieldAsync()
                    expected += x
                    assertEquals(expected, worker.waiterService.total)
                    assertTrue(future.isDone())
                    x += 1
                }
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    // Cancels each promise before the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun preCancelTest() {
        val worker = WaiterWorker()
        val testService = object : FinishableService() {
            override val worker = worker
            override suspend fun run() {
                var x = 0
                while (x < futureCount) {
                    val future = CoroutineFuture<Int>()
                    val promise = future.promise()
                    promise.cancel()
                    worker.serviceWriter.offer(future)
                    assertTrue(promise.isDone())
                    assertTrue(promise.isCancelled())
                    yieldAsync()
                    assertEquals(x + 1, worker.waiterService.cancelled)
                    assertTrue(future.isCancelled())
                    assertTrue(future.isDone())
                    x += 1
                }
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    // Cancels each promise after the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun postCancelTest() {
        val worker = WaiterWorker()
        val testService = object : FinishableService() {
            override val worker = worker
            override suspend fun run() {
                var x = 0
                while (x < futureCount) {
                    val future = CoroutineFuture<Int>()
                    val promise = future.promise()
                    worker.serviceWriter.offer(future)
                    promise.cancel()
                    assertTrue(promise.isDone())
                    assertTrue(promise.isCancelled())
                    yieldAsync()
                    assertEquals(x + 1, worker.waiterService.cancelled)
                    assertTrue(future.isCancelled())
                    assertTrue(future.isDone())
                    x += 1
                }
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    // Completes each promise exceptionally before the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun preExceptionTest() {
        val worker = WaiterWorker()
        val testService = object : FinishableService() {
            override val worker = worker
            override suspend fun run() {
                var x = 0
                while (x < futureCount) {
                    val future = CoroutineFuture<Int>()
                    val promise = future.promise()
                    promise.completeExceptionally(Exception())
                    worker.serviceWriter.offer(future)
                    assertTrue(promise.isDone())
                    yieldAsync()
                    assertEquals(x + 1, worker.waiterService.exceptions)
                    assertTrue(future.isDone())
                    x += 1
                }
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    // Completes each promise exceptionally after the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun postExceptionTest() {
        val worker = WaiterWorker()
        val testService = object : FinishableService() {
            override val worker = worker
            override suspend fun run() {
                var x = 0
                while (x < futureCount) {
                    val future = CoroutineFuture<Int>()
                    val promise = future.promise()
                    worker.serviceWriter.offer(future)
                    promise.completeExceptionally(Exception())
                    assertTrue(promise.isDone())
                    yieldAsync()
                    assertEquals(x + 1, worker.waiterService.exceptions)
                    assertTrue(future.isDone())
                    x += 1
                }
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    // Ensures any further actions after a cancellation do nothing
    @RepeatedTest(heavyRepeatCount)
    fun singleCancelTest() {
        val future = CoroutineFuture<Int>()
        val promise = future.promise()
        promise.cancel()
        assertFalse(promise.cancel())
        assertFalse(promise.complete(random.nextInt()))
        assertFalse(promise.completeExceptionally(Exception()))
    }

    // Ensures any further actions after a complete do nothing
    @RepeatedTest(heavyRepeatCount)
    fun singleCompleteTest() {
        val future = CoroutineFuture<Int>()
        val promise = future.promise()
        promise.complete(random.nextInt())
        assertFalse(promise.complete(random.nextInt()))
        assertFalse(promise.completeExceptionally(Exception()))
        assertFalse(promise.cancel())
    }

    // Ensures any further actions after a completeExceptionally do nothing
    @RepeatedTest(heavyRepeatCount)
    fun singleCompleteExceptionallyTest() {
        val future = CoroutineFuture<Int>()
        val promise = future.promise()
        promise.completeExceptionally(Exception())
        assertFalse(promise.completeExceptionally(Exception()))
        assertFalse(promise.complete(random.nextInt()))
        assertFalse(promise.cancel())
    }

    // Ensures poll() will eventually produce the provided value
    @RepeatedTest(heavyRepeatCount)
    fun pollTest() {
        val worker = CoroutineWorker()
        val testService = object : FinishableService() {
            override val worker = worker
            override suspend fun run() {
                val future = CoroutineFuture<Int>()
                val promise = future.promise()
                assertNull(future.poll())
                val randomNumber = random.nextInt()
                promise.complete(randomNumber)
                yieldAsync()
                assertEquals(randomNumber, future.poll())
                finished = true
            }
        }
        worker.addService(testService)
        validateFinishes(worker)
    }

    // Ensures only a single promise can be made
    @RepeatedTest(heavyRepeatCount)
    fun illegalDoublePromiseTest() {
        val future = CoroutineFuture<Int>()
        future.promise()
        assertThrows(IllegalStateException::class.java, { future.promise() })
    }
}
