package tech.pronghorn.coroutines.awaitable

import org.junit.Test
import tech.pronghorn.coroutines.DummyWorker
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.service.QueueService
import tech.pronghorn.util.PronghornTest

class ExternalQueueTests : PronghornTest() {
    class TestQueueService(override val worker: CoroutineWorker) : QueueService<Int>() {
        val queue = ExternalQueue(64, this)

        override fun getQueueWriter(): QueueWriter<Int> = throw NotImplementedError()

        suspend override fun run() = throw NotImplementedError()
    }

    @Test
    fun foo() {
        val queue = TestQueueService(DummyWorker()).queue
//        val x = queue.queue
        val reader = queue.queueReader
        val writer = queue.queueWriter


        TODO()
    }
}
