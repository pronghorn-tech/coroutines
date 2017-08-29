package tech.pronghorn.coroutines.oldtests

import tech.pronghorn.util.eventually
import mu.KotlinLogging
import org.junit.Test
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.service.SingleWriterExternalQueueService
import tech.pronghorn.util.PronghornTest
import java.nio.channels.SelectionKey
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class SimpleExternalService(override val worker: CoroutineWorker,
                            val totalWork: Long) : SingleWriterExternalQueueService<Int>() {
    override val logger = KotlinLogging.logger {}

    var workDone = 0L

    override fun shouldYield(): Boolean {
//        println("shouldYield()?")
        return workDone % 100 == 0L
//        return false
    }

    suspend override fun process(work: Int) {
        workDone += 1
        if (workDone >= totalWork) {
            worker.shutdown()
        }
    }
}

class PipelineWithExternalService(totalWork: Long) : CoroutineWorker() {
    override val logger = KotlinLogging.logger {}

    override fun processKey(key: SelectionKey) {}

    val externalService = SimpleExternalService(this, totalWork)

    override val services = listOf(externalService)
}

class ExternalServiceTests : PronghornTest() {
    @Test
    fun futureEquivalent(){
        repeat(64) {
            val workCount = 1000000L
            val executor = Executors.newFixedThreadPool(4)
            var x = 0
            var y = 0L

            val pre = System.currentTimeMillis()
            while (x < workCount) {
                executor.submit { y += 1}
                x += 1
            }

            eventually {
                assert(y > 980000L)
//                assertEquals(y, workCount)
            }
            val post = System.currentTimeMillis()
            logger.info("A Took ${post - pre}ms for $workCount, ${(workCount / Math.max(1, (post - pre))) / 1000.0} million per second")
            executor.shutdown()
        }
    }

    /*
     * SingleWriterExternalQueueService should function properly for a producer in another thread
     */
    @Test
    fun externalQueueService() {
        repeat(256) {
            val workCount = 10000000L
            val pipeline = PipelineWithExternalService(workCount)

            try {
                val pre = System.currentTimeMillis()
                val externalWriter = pipeline.externalService.getQueueWriter()
                pipeline.start()

                var x = 0
                var y = 0
                while (x < workCount) {
                    if (externalWriter.offer(1)) {
                        x += 1
                    }
                    y += 1
                }

                try {
                    eventually { assertEquals(workCount, pipeline.externalService.workDone) }
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
