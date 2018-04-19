/*
package tech.pronghorn.coroutines.awaitable.queue

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.awaitable.future.CoroutineFuture
import tech.pronghorn.coroutines.core.*
import tech.pronghorn.test.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class SpmcPreallocatedQueueTests : PronghornTest() {
    class DataHolder(var longA: Long = 0,
                     var longB: Long = 0)

    @RepeatedTest(lightRepeatCount)
    fun singleThreadStressTest() {
        val buffer = SpmcPreallocatedQueue(1024, 2, { DataHolder() })
        val writer = buffer.writer
        val readerA = buffer.getReader(0)
        val readerB = buffer.getReader(1)

        val count = 100000000L
        val pre = System.currentTimeMillis()
        var x = 0L
        var total = 0L
        while (x < count) {
            while (x < count && writer.offer { it.longA = x; it.longB = x }) {
                x += 1
            }
            writer.publish()

            var result = readerA.poll()
            while (result != null) {
                total += result.longA + result.longB
                result = readerA.poll()
            }
            readerA.commit()

            result = readerB.poll()
            while (result != null) {
                total += result.longA + result.longB
                result = readerB.poll()
            }
            readerB.commit()
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

    class WriteThread(val buffer: SpmcPreallocatedQueue<DataHolder>,
                      val count: Long) : Thread() {
        internal var a00 = 0L; internal var a01 = 0L; internal var a02 = 0L; internal var a03 = 0L
        internal var a04 = 0L; internal var a05 = 0L; internal var a06 = 0L; internal var a07 = 0L
        internal var a08 = 0L; internal var a09 = 0L; internal var a10 = 0L; internal var a11 = 0L
        internal var a12 = 0L; internal var a13 = 0L; internal var a14 = 0L; internal var a15 = 0L
        internal var b00 = 0L; internal var b01 = 0L; internal var b02 = 0L; internal var b03 = 0L
        internal var b04 = 0L; internal var b05 = 0L; internal var b06 = 0L; internal var b07 = 0L
        internal var b08 = 0L; internal var b09 = 0L; internal var b10 = 0L; internal var b11 = 0L
        internal var b12 = 0L; internal var b13 = 0L; internal var b14 = 0L; internal var b15 = 0L

        public fun getAll(): Long {
            return a00 + a01 + a02 + a03 + a04 + a05 + a06 + a07 + a08 + a09 + a10 + a11 + a12 + a13 + a14 + a15 + b00 + b01 + b02 + b03 + b04 + b05 + b06 + b07 + b08 + b09 + b10 + b11 + b12 + b13 + b14 + b15
        }

        public fun setAll(v: Long) {
            a00 = v; a01 = v; a02 = v; a03 = v; a04 = v; a05 = v; a06 = v; a07 = v; a08 = v; a09 = v; a10 = v; a11 = v; a12 = v; a13 = v; a14 = v; a15 = v; b00 = v; b01 = v; b02 = v; b03 = v; b04 = v; b05 = v; b06 = v; b07 = v; b08 = v; b09 = v; b10 = v; b11 = v; b12 = v; b13 = v; b14 = v; b15 = v
        }

        init {
            setAll(1L)
        }

        override fun run() {
            val writer = buffer.writer
            var x = 0L
            while (x < count) {
                val preX = x
                var sent = 0
                while (writer.offer { it.longA = x; it.longB = x; }) {
                    x += 1
                    sent += 1
                    if (x >= count || sent == 1024) {
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

    class ReadThread(val buffer: SpmcPreallocatedQueue<DataHolder>,
                     val count: Long,
                     val extraWork: Long,
                     val readerNum: Int) : Thread() {
        internal var a00 = 0L; internal var a01 = 0L; internal var a02 = 0L; internal var a03 = 0L
        internal var a04 = 0L; internal var a05 = 0L; internal var a06 = 0L; internal var a07 = 0L
        internal var a08 = 0L; internal var a09 = 0L; internal var a10 = 0L; internal var a11 = 0L
        internal var a12 = 0L; internal var a13 = 0L; internal var a14 = 0L; internal var a15 = 0L
        internal var b00 = 0L; internal var b01 = 0L; internal var b02 = 0L; internal var b03 = 0L
        internal var b04 = 0L; internal var b05 = 0L; internal var b06 = 0L; internal var b07 = 0L
        internal var b08 = 0L; internal var b09 = 0L; internal var b10 = 0L; internal var b11 = 0L
        internal var b12 = 0L; internal var b13 = 0L; internal var b14 = 0L; internal var b15 = 0L

        public fun getAll(): Long {
            return a00 + a01 + a02 + a03 + a04 + a05 + a06 + a07 + a08 + a09 + a10 + a11 + a12 + a13 + a14 + a15 + b00 + b01 + b02 + b03 + b04 + b05 + b06 + b07 + b08 + b09 + b10 + b11 + b12 + b13 + b14 + b15
        }

        public fun setAll(v: Long) {
            a00 = v; a01 = v; a02 = v; a03 = v; a04 = v; a05 = v; a06 = v; a07 = v; a08 = v; a09 = v; a10 = v; a11 = v; a12 = v; a13 = v; a14 = v; a15 = v; b00 = v; b01 = v; b02 = v; b03 = v; b04 = v; b05 = v; b06 = v; b07 = v; b08 = v; b09 = v; b10 = v; b11 = v; b12 = v; b13 = v; b14 = v; b15 = v
        }

        init {
            setAll(1L)
        }

        var total = 0L
        var extra = 0L

        fun doExtra() {
            var y = 0
            while(y < extraWork){
                y += 1
            }
            extra += y
        }

        override fun run() {
            val reader = buffer.getReader(readerNum)
            var x = 0L
            while (x < count) {
                val preX = x
                var result = reader.poll()
                var read = 0
                while (result != null) {
                    x += 1
                    total += result.longA + result.longB
                    doExtra()
                    if(result.longA >= count - buffer.readerCount) {
                        // end condition
                        x = count
                        break
                    }
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
            println("Read finished ${System.currentTimeMillis()}, $extra")
        }
    }

    @RepeatedTest(lightRepeatCount)
    fun multiThreadStressTest() {
        val readThreadCount = 4
        val buffer = SpmcPreallocatedQueue(4096, readThreadCount, { DataHolder() })
        val count = 10000000L

        val extraWork = 1000L
        val writeThread = WriteThread(buffer, count)
        val readThreads = (0 until readThreadCount).map { ReadThread(buffer, count, extraWork, it) }

        readThreads.forEach(Thread::start)
        val pre = System.currentTimeMillis()
        writeThread.start()

        eventually(Duration.ofSeconds(10)) {
            assertFalse(writeThread.isAlive)
            readThreads.forEach { assertFalse(it.isAlive) }
        }

        writeThread.join()
        readThreads.forEach(Thread::join)
        val post = System.currentTimeMillis()

        var z = 0
        var expectedTotal = 0L
        while (z < count) {
            expectedTotal += z * 2
            z += 1
        }

        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        var readTotal = 0L
        readThreads.forEach { readTotal += it.total }
        assertEquals(expectedTotal, readTotal)
    }

    class WriteService(override val worker: CoroutineWorker,
                       val buffer: SpmcPreallocatedQueue<DataHolder>,
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
            println("write done")
            finished.incrementAndGet()
            val future = CoroutineFuture<Unit>()
            future.awaitAsync()
        }
    }

    class ReadService(override val worker: CoroutineWorker,
                      val buffer: SpmcPreallocatedQueue<DataHolder>,
                      val count: Long,
                      val finished: AtomicInteger,
                      val extraWork: Long,
                      val readerNum: Int) : Service() {
        var total = 0L
        var extra = 0L

        fun doExtra() {
            var y = 0
            while(y < extraWork){
                y += 1
            }
            extra += y
        }

        override suspend fun run() {
            val reader = buffer.getReader(readerNum)
            var x = 0L
            while (x < count) {
                var result: DataHolder? = reader.poll() ?: reader.awaitAsync()
                var read = 0
                while (result != null) {
                    x += 1
                    total += result.longA + result.longB
                    doExtra()
                    if(result.longA >= count - buffer.readerCount) {
                        // end condition
                        x = count
                        break
                    }
                    read += 1
                    if (read == 1024) {
                        break
                    }
                    result = reader.poll()
                }
                reader.commit()
            }
            println("read done $extra")
            finished.incrementAndGet()
            val future = CoroutineFuture<Unit>()
            future.awaitAsync()
        }
    }

    class WriteWorker(buffer: SpmcPreallocatedQueue<DataHolder>,
                      count: Long,
                      finished: AtomicInteger) : CoroutineWorker() {
        val writeService = WriteService(this, buffer, count, finished)
        override val initialServices = listOf(writeService)
    }

    class ReadWorker(buffer: SpmcPreallocatedQueue<DataHolder>,
                     count: Long,
                     finished: AtomicInteger,
                     extraWork: Long,
                     readerNum: Int) : CoroutineWorker() {
        val readService = ReadService(this, buffer, count, finished, extraWork, readerNum)
        override val initialServices = listOf(readService)
    }

    @RepeatedTest(lightRepeatCount)
    fun multiThreadWorkerStressTest() {
        val readerCount = 7
        val buffer = SpmcPreallocatedQueue(4096, readerCount, { DataHolder() })
        val count = 10000000L
        val extraWork = 1000L
        val finished = AtomicInteger(0)
        val writeWorker = WriteWorker(buffer, count, finished)
        val readWorkers = (0 until readerCount).map { ReadWorker(buffer, count, finished, extraWork, it) }
        val app = object : CoroutineApplication<CoroutineWorker>() {
            override val workers: Set<CoroutineWorker> = readWorkers.toSet() + writeWorker
        }

        val pre = System.currentTimeMillis()
        app.start()
        eventually(Duration.ofSeconds(3000), Duration.ofMillis(1)) {
            assertEquals(1 + readWorkers.size, finished.get())
        }
        val post = System.currentTimeMillis()

        app.shutdown()
        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        var readTotal = 0L
        readWorkers.forEach { readTotal += it.readService.total }
        assertEquals(writeWorker.writeService.total, readTotal)
    }
}
*/
