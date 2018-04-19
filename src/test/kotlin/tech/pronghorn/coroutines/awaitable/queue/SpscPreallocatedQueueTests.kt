/*
package tech.pronghorn.coroutines.awaitable.queue

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.awaitable.future.CoroutineFuture
import tech.pronghorn.coroutines.core.*
import tech.pronghorn.test.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class SpscPreallocatedQueueTests : PronghornTest() {
    class DataHolder(var longA: Long = 0,
                     var longB: Long = 0)

    @RepeatedTest(lightRepeatCount)
    fun singleThreadStressTest() {
        val buffer = SpscPreallocatedQueue(1024, { DataHolder() })
        val writer = buffer.writer
        val reader = buffer.reader

        val count = 100000000L
        val pre = System.currentTimeMillis()
        var x = 0L
        var total = 0L
        while (x < count) {
            while (x < count && writer.offer { it.longA = x; it.longB = x }) {
                x += 1
            }
            writer.publish()

            var result = reader.poll()
            while (result != null) {
                total += result.longA + result.longB
                result = reader.poll()
            }
            reader.commit()
        }

        val post = System.currentTimeMillis()

        var z = 0
        var expectedTotal = 0L
        while (z < count) {
            expectedTotal += z * 2
            z += 1
        }

        println("$total : $expectedTotal")
        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        assertEquals(expectedTotal, total)
    }

    class WriteThread(val buffer: SpscPreallocatedQueue<DataHolder>,
                      val count: Long) : Thread() {
        public var writer: SpscPreallocatedQueue.Writer<DataHolder>? = null
        override fun run() {
            val writer = buffer.writer
            this.writer = writer
            var x = 0L
            while (x < count) {
                val preX = x
                var sent = 0
                while (x < count && writer.offer { it.longA = x; it.longB = x }) {
                    x += 1
                    sent += 1
                    if (sent == 1024) {
                        break
                    }
                }
                if (x > preX) {
                    writer.publish()
                }
            }
            println("Write finished ${System.currentTimeMillis()}")
        }
    }

    class ReadThread(val buffer: SpscPreallocatedQueue<DataHolder>,
                     val count: Long) : Thread() {
        var total = 0L
        public var reader: SpscPreallocatedQueue.Reader<DataHolder>? = null

        override fun run() {
            val reader = buffer.reader
            this.reader = reader
            var x = 0L
            while (x < count) {
                val preX = x
                var result = reader.poll()
                var read = 0
                while (result != null) {
                    x += 1
                    total += result.longA + result.longB
                    read += 1
                    if (read == 1024) {
                        break
                    }
                    result = reader.poll()
                }
                if (x > preX) {
                    reader.commit()
                }
            }
            println("Read finished ${System.currentTimeMillis()}")
        }
    }

    @RepeatedTest(lightRepeatCount)
    fun multiThreadStressTest() {
        val count = 100000000L
        val buffer = SpscPreallocatedQueue(4096, { DataHolder() })
        val writeThread = WriteThread(buffer, count)
        val readThread = ReadThread(buffer, count)

        readThread.start()
        val pre = System.currentTimeMillis()
        writeThread.start()

        writeThread.join()
        readThread.join()
        val post = System.currentTimeMillis()

        var z = 0
        var expectedTotal = 0L
        while (z < count) {
            expectedTotal += z * 2
            z += 1
        }

        println("${readThread.total} : $expectedTotal")
        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        assertEquals(expectedTotal, readThread.total)
    }

    class WriteService(override val worker: CoroutineWorker,
                       val buffer: SpscPreallocatedQueue<DataHolder>,
                       val count: Long,
                       val finished: AtomicInteger) : Service() {

        var total = 0L
        override suspend fun run() {
            val writer = buffer.writer
            var x = 0L
            while (x < count) {
                var sent = 0
                writer.offer { it.longA = x; it.longB = x } || writer.addAsync { it.longA = x; it.longB = x }
                total += x * 2
                x += 1
                while (x < count && writer.offer { it.longA = x; it.longB = x }) {
                    total += x * 2
                    x += 1
                    sent += 1
                    if (sent == 1024) {
                        break
                    }
                }

                writer.publish()
            }
            finished.incrementAndGet()
            val future = CoroutineFuture<Unit>()
            future.awaitAsync()
        }
    }

    class ReadService(override val worker: CoroutineWorker,
                      val buffer: SpscPreallocatedQueue<DataHolder>,
                      val count: Long,
                      val finished: AtomicInteger) : Service() {
        var total = 0L

        override suspend fun run() {
            val reader = buffer.reader
            var x = 0L
            while (x < count) {
                var result: DataHolder? = reader.poll() ?: reader.awaitAsync()
                var read = 0
                while (result != null) {
                    x += 1
                    total += result.longA + result.longB
                    read += 1
                    if (read == 1024) {
                        break
                    }
                    result = reader.poll()
                }
                reader.commit()
            }
            finished.incrementAndGet()
            val future = CoroutineFuture<Unit>()
            future.awaitAsync()
        }
    }

    class WriteWorker(buffer: SpscPreallocatedQueue<DataHolder>,
                      count: Long,
                      finished: AtomicInteger) : CoroutineWorker() {
        val writeService = WriteService(this, buffer, count, finished)
        override val initialServices = listOf(writeService)
    }

    class ReadWorker(buffer: SpscPreallocatedQueue<DataHolder>,
                     count: Long,
                     finished: AtomicInteger) : CoroutineWorker() {
        val readService = ReadService(this, buffer, count, finished)
        override val initialServices = listOf(readService)
    }

    @RepeatedTest(lightRepeatCount * 16)
    fun multiThreadWorkerStressTest() {
        val buffer = SpscPreallocatedQueue(4096, { DataHolder() })
        val count = 100000000L
        val finished = AtomicInteger(0)
        val writeWorker = WriteWorker(buffer, count, finished)
        val readWorker = ReadWorker(buffer, count, finished)
        val app = object : CoroutineApplication<CoroutineWorker>() {
            override val workers: Set<CoroutineWorker> = setOf(writeWorker, readWorker)
        }

        val pre = System.currentTimeMillis()
        app.start()
        eventually(Duration.ofSeconds(3000), Duration.ofMillis(1)) {
            assertEquals(2, finished.get())
        }
        val post = System.currentTimeMillis()

        app.shutdown()
        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        assertEquals(writeWorker.writeService.total, readWorker.readService.total)
    }
}
*/
