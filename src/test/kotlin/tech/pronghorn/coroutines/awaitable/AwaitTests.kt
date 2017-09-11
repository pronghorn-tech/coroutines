package tech.pronghorn.coroutines.awaitable

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.service.Service
import tech.pronghorn.coroutines.service.SingleWriterExternalQueueService
import tech.pronghorn.test.*
import java.nio.channels.SelectionKey
import java.util.ArrayDeque
import java.util.Queue

class AwaitTests : PronghornTest() {
    class AwaitingService(override val worker: AwaitingWorker,
                          private val futures: List<InternalFuture<Int>>) : Service() {
        suspend override fun run() {
            when (futures.size) {
                1 -> {
                    worker.done = await(futures.first())
                }
                2 -> {
                    val (futureA, futureB) = futures
                    val (a, b) = await(futureA, futureB)
                    worker.done = a + b
                }
                3 -> {
                    val (futureA, futureB, futureC) = futures
                    val (a, b, c) = await(futureA, futureB, futureC)
                    worker.done = a + b + c
                }
                4 -> {
                    val (futureA, futureB, futureC, futureD) = futures
                    val (a, b, c, d) = await(futureA, futureB, futureC, futureD)
                    worker.done = a + b + c + d
                }
                else -> throw Exception("Awaiting five awaiters at once isn't currently supported.")
            }
        }
    }

    class CompletingService(override val worker: AwaitingWorker,
                            private val promises: Queue<InternalFuture.InternalPromise<Int>>) : SingleWriterExternalQueueService<Int>() {
        suspend override fun process(work: Int) {
            val promise = promises.poll()
            promise.complete(work)
        }
    }

    class AwaitingWorker(futureCount: Int) : CoroutineWorker() {
        @Volatile
        var done = 0

        private val futures = List(futureCount, { InternalFuture<Int>() })
        private val promises = ArrayDeque(futures.map(InternalFuture<Int>::promise))
        private val completingService = CompletingService(this, promises)
        val externalWriter = completingService.getQueueWriter()

        override val services: List<Service> = listOf(
                AwaitingService(this, futures),
                completingService
        )

        override fun processKey(key: SelectionKey) {}
    }

    fun variableAwait(futureCount: Int) {
        val worker = AwaitingWorker(futureCount)
        worker.start()

        try {
            assertEquals(0, worker.done)

            var totalValue = 0
            for (x in 1..futureCount) {
                val value = random.nextInt(64)
                totalValue += value
                worker.externalWriter.offer(value)
            }

            eventually {
                assertEquals(totalValue, worker.done)
            }
        }
        finally {
            worker.shutdown()
        }
    }

    /*
     * Tests awaiting for one internal future utilizing the worker/services above
     */
    @RepeatedTest(repeatCount)
    fun await1() {
        variableAwait(1)
    }

    /*
     * Tests awaiting for two internal futures utilizing the worker/services above
     */
    @RepeatedTest(repeatCount)
    fun await2() {
        variableAwait(2)
    }

    /*
     * Tests awaiting for two internal futures utilizing the worker/services above
     */
    @RepeatedTest(repeatCount)
    fun await3() {
        variableAwait(3)
    }

    /*
     * Tests awaiting for two internal futures utilizing the worker/services above
     */
    @RepeatedTest(repeatCount)
    fun await4() {
        variableAwait(4)
    }
}
