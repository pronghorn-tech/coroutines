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
import tech.pronghorn.coroutines.PronghornTestWithWorkerCleanup
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.coroutines.awaitable.queue.ExternalQueue
import tech.pronghorn.coroutines.core.*
import tech.pronghorn.coroutines.services.ExternalQueueService
import tech.pronghorn.plugins.logging.LoggingPlugin
import tech.pronghorn.test.eventually
import tech.pronghorn.test.heavyRepeatCount
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

class ExternalPromiseTests : PronghornTestWithWorkerCleanup() {
    class ExternalFutureWaiterService(override val worker: CoroutineWorker) : ExternalQueueService<CoroutineFuture<Int>>(worker) {
        @Volatile
        var total = 0
        @Volatile
        var cancelled = 0
        @Volatile
        var exceptions = 0

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        suspend override fun process(future: CoroutineFuture<Int>) {
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
        }
    }

    class WaiterWorker : CoroutineWorker() {
        val waiterService = ExternalFutureWaiterService(this)
        val serviceWriter = waiterService.getQueueWriter()
        override val initialServices = listOf(waiterService)
    }

    private fun <T> getFutureAndPromiseInWorker(worker: CoroutineWorker): Pair<CoroutineFuture<T>, CoroutinePromise<T>> {
        var promise: CoroutinePromise<T>? = null
        var future: CoroutineFuture<T>? = null
        worker.executeInWorker {
            future = CoroutineFuture()
            promise = future!!.externalPromise()
        }
        eventually {
            assertNotNull(future)
            assertNotNull(promise)
        }
        return Pair(future!!, promise!!)
    }

    val futureCount = 16

    // Completes each promise before the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun preCompleteTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        var x = 0
        var expected = 0
        while (x < futureCount) {
            val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
            promise.complete(x)
            externalWorker.executeInWorker {
                worker.serviceWriter.offer(future)
            }
            assertTrue(promise.isDone())
            expected += x
            eventually {
                assertEquals(expected, worker.waiterService.total)
                assertTrue(future.isDone())
            }
            x += 1
        }
    }

    // Completes each promise after the services awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun postCompleteTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        var x = 0
        var expected = 0
        while (x < futureCount) {
            val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
            externalWorker.executeInWorker {
                worker.serviceWriter.offer(future)
            }
            if (random.nextBoolean()) {
                Thread.sleep(1)
            }
            promise.complete(x)
            assertTrue(promise.isDone())
            expected += x
            eventually {
                assertEquals(expected, worker.waiterService.total)
                assertTrue(future.isDone())
            }
            x += 1
        }
    }

    // Cancels each promise before the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun preCancelTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        var x = 0
        while (x < futureCount) {
            val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
            promise.cancel()
            externalWorker.executeInWorker {
                worker.serviceWriter.offer(future)
            }
            assertTrue(promise.isDone())
            assertTrue(promise.isCancelled())
            eventually {
                assertEquals(x + 1, worker.waiterService.cancelled)
                assertTrue(future.isCancelled())
                assertTrue(future.isDone())
            }
            x += 1
        }
    }

    // Cancels each promise after the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun postCancelTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        var x = 0
        while (x < futureCount) {
            val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
            externalWorker.executeInWorker {
                worker.serviceWriter.offer(future)
            }
            if (random.nextBoolean()) {
                Thread.sleep(1)
            }
            promise.cancel()
            assertTrue(promise.isDone())
            assertTrue(promise.isCancelled())
            eventually {
                assertEquals(x + 1, worker.waiterService.cancelled)
                assertTrue(future.isCancelled())
                assertTrue(future.isDone())
            }
            x += 1
        }
    }

    // Completes each promise exceptionally before the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun preExceptionTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        var x = 0
        while (x < futureCount) {
            val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
            promise.completeExceptionally(Exception())
            externalWorker.executeInWorker {
                worker.serviceWriter.offer(future)
            }
            assertTrue(promise.isDone())
            eventually {
                assertEquals(x + 1, worker.waiterService.exceptions)
                assertTrue(future.isDone())
            }
            x += 1
        }
    }

    // Completes each promise exceptionally after the service awaits the future
    @RepeatedTest(heavyRepeatCount)
    fun postExceptionTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        var x = 0
        while (x < futureCount) {
            val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
            externalWorker.executeInWorker {
                worker.serviceWriter.offer(future)
            }
            if (random.nextBoolean()) {
                Thread.sleep(1)
            }
            promise.completeExceptionally(Exception())
            assertTrue(promise.isDone())
            eventually {
                assertEquals(x + 1, worker.waiterService.exceptions)
                assertTrue(future.isDone())
            }
            x += 1
        }
    }

    // Ensures any further actions after a cancellation do nothing
    @RepeatedTest(heavyRepeatCount)
    fun singleCancelTest() {
        val worker = getWorker()
        val (_, promise) = getFutureAndPromiseInWorker<Int>(worker)
        promise.cancel()
        assertFalse(promise.cancel())
        assertFalse(promise.complete(random.nextInt()))
        assertFalse(promise.completeExceptionally(Exception()))
    }

    // Ensures any further actions after a complete do nothing
    @RepeatedTest(heavyRepeatCount)
    fun singleCompleteTest() {
        val worker = getWorker()
        val (_, promise) = getFutureAndPromiseInWorker<Int>(worker)

        eventually {
            assertNotNull(promise)
        }

        promise.complete(random.nextInt())
        assertFalse(promise.complete(random.nextInt()))
        assertFalse(promise.completeExceptionally(Exception()))
        assertFalse(promise.cancel())
    }

    // Ensures any further actions after a completeExceptionally do nothing
    @RepeatedTest(heavyRepeatCount)
    fun singleCompleteExceptionallyTest() {
        val worker = getWorker()
        val (_, promise) = getFutureAndPromiseInWorker<Int>(worker)
        promise.completeExceptionally(Exception())
        assertFalse(promise.completeExceptionally(Exception()))
        assertFalse(promise.complete(random.nextInt()))
        assertFalse(promise.cancel())
    }

    // Ensures poll() will eventually produce the provided value
    @RepeatedTest(heavyRepeatCount)
    fun pollTest() {
        val worker = getWorker { WaiterWorker() }
        val externalWorker = getWorker()

        val (future, promise) = getFutureAndPromiseInWorker<Int>(worker)
        assertNull(future.poll())
        externalWorker.executeInWorker {
            worker.serviceWriter.offer(future)
        }
        val randomNumber = random.nextInt()
        promise.complete(randomNumber)
        eventually {
            assertEquals(randomNumber, future.poll())
        }
    }

    // Ensures only a single promise can be made
    @RepeatedTest(heavyRepeatCount)
    fun illegalDoublePromiseTest() {
        val worker = getWorker()
        var thrown: Throwable? = null
        worker.executeInWorker {
            try {
                val future = CoroutineFuture<Int>()
                future.externalPromise()
                future.externalPromise()
            }
            catch (ex: Throwable) {
                thrown = ex
            }
        }

        eventually {
            assertNotNull(thrown)
            assert(thrown!!.javaClass == IllegalStateException::class.java)
        }
    }

    @RepeatedTest(16)
    fun stressTest() {
        val logger = LoggingPlugin.get(javaClass)
        val workerA = getWorker(false)
        val workerB = getWorker(false)

        val externalQueue = ExternalQueue<CoroutinePromise<Long>>(1024, workerA)
        val count = 1000000L
        var total = 0L

        val serviceA = object : Service() {
            override val worker: CoroutineWorker = workerA
            var x = 0

            suspend override fun run() {
                val writer = externalQueue.writer
                logger.error { "Starting" }
                while (x < count) {
                    val future = CoroutineFuture<Long>({ result ->
                        if (total % 100000 == 0L) {
                            val now = System.nanoTime()
                            logger.info { "RETURNED after ${now - result} ns to process future.complete() " }
                        }
                        total += 1
                    })
                    val promise = future.externalPromise()
                    writer.offer(promise) || writer.addAsync(promise)
                    x += 1
                }
            }
        }
        workerA.addService(serviceA)

        workerB.executeSuspendingInWorker {
            val reader = externalQueue.reader
            try {
                var x = 0
                while (x < count) {
                    val promise = reader.awaitAsync()
                    promise.complete(System.nanoTime())
                    x += 1
                }
            }
            catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }

        val before = System.currentTimeMillis()

        workerA.start()
        workerB.start()

        eventually(Duration.ofSeconds(10)) {
            assertEquals(count, total)
        }
        val after = System.currentTimeMillis()

        println("Took ${after - before} ms for $count, which is ${count * 1000 / (after - before)} per second")
    }
}
