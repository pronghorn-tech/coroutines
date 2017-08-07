package tech.pronghorn.coroutines.oldtests

import tech.pronghorn.coroutines.awaitable.CoroutineFuture
import tech.pronghorn.coroutines.service.SingleWriterExternalQueueService
import eventually
import mu.KotlinLogging
import org.junit.Test
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.test.CDBTest
import java.nio.channels.SelectionKey
import kotlin.test.assertEquals

class FutureService(override val worker: CoroutineWorker,
                    val totalWork: Long) : SingleWriterExternalQueueService<CoroutineFuture.CoroutinePromise<Int>>() {
    override val logger = KotlinLogging.logger {}

    var workDone = 0L

    override fun shouldYield(): Boolean {
        return false
    }

    suspend override fun process(work: CoroutineFuture.CoroutinePromise<Int>) {
        workDone += 1
        work.complete(1)
        if (workDone >= totalWork) {
            worker.shutdown()
        }
    }
}

class PipelineWithFuture(totalWork: Long) : CoroutineWorker() {
    override val logger = KotlinLogging.logger {}

    override fun processKey(key: SelectionKey) {}

    val futureService = FutureService(this, totalWork)

    override val services = listOf(futureService)
}

class FutureGetTests : CDBTest() {

    /*
     * Futures should function properly across threads with get()
     */
    @Test
    fun futuresAcrossThreads() {
        repeat(256) {
            val workCount = 1000000L
            val pipeline = PipelineWithFuture(workCount)

            try {
                val pre = System.currentTimeMillis()
                val externalWriter = pipeline.futureService.getQueueWriter()
                pipeline.start()

                var x = 0
                while (x < workCount) {
                    val future = CoroutineFuture<Int>()
                    externalWriter.offer(future.promise())
                    while (!future.isDone) {

                    }
                    x += future.get()
                }

                try {
                    eventually { assertEquals(workCount, pipeline.futureService.workDone) }
                } catch (ex: AssertionError) {
                    ex.printStackTrace()
                }
                val post = System.currentTimeMillis()
                logger.info("A Took ${post - pre}ms for $workCount, ${(workCount / Math.max(1, (post - pre))) / 1000.0} million per second")
            } finally {
                pipeline.shutdown()
            }
        }
    }
}
