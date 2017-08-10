package tech.pronghorn.coroutines.oldtests

import tech.pronghorn.coroutines.service.InternalQueueService
import tech.pronghorn.coroutines.service.Service
import eventually
import mu.KotlinLogging
import org.junit.Test
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.test.CDBTest
import java.nio.channels.SelectionKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
class _EmptyPipeline : CoroutineWorker() {
    override val logger = KotlinLogging.logger {}

    override fun processKey(key: SelectionKey) {}

    override val services: List<Service> = emptyList()
}

class _CountdownPipeline(val totalWork: Long) : CoroutineWorker() {
    override val logger = KotlinLogging.logger {}

    override fun processKey(key: SelectionKey) {}

    val countdownService = CountdownService(this, totalWork)

    override val services = listOf(countdownService)

    override fun onStart() {
        val countdownWriter = countdownService.getQueueWriter()
        countdownWriter.offer(1)
    }
}

class _CountdownService(override val worker: CoroutineWorker,
                        val totalWork: Long) : InternalQueueService<Int>() {
    override val logger = KotlinLogging.logger {}
    var workDone = 0L

    override fun shouldYield(): Boolean {
        return true
    }

    suspend override fun process(work: Int): Boolean {
        workDone += 1
        if (workDone >= totalWork) {
            worker.shutdown()
            return true
        }
        return false
    }
}

class _PingService(override val worker: CoroutineWorker,
                   val totalWork: Long) : InternalQueueService<Int>() {
    override val logger = KotlinLogging.logger {}
    var pongService: PongService? = null

    var workDone = 0L

    val pongWriter by lazy {
        pongService?.getQueueWriter()
    }

    override fun shouldYield(): Boolean = workDone % 100 == 0L

    suspend override fun process(work: Int): Boolean {
        workDone += 1
        if (workDone + (pongService?.workDone ?: 0) >= totalWork) {
            worker.shutdown()
        } else {
            pongWriter!!.addAsync(1)
        }
        return true
    }
}

class _PongService(override val worker: CoroutineWorker,
                  val totalWork: Long) : InternalQueueService<Int>() {
    override val logger = KotlinLogging.logger {}
    var pingService: PingService? = null

    val pingWriter by lazy {
        pingService?.getQueueWriter()
    }
    var workDone = 0L

    override fun shouldYield(): Boolean = workDone % 100 == 0L

    suspend override fun process(work: Int): Boolean {
        workDone += 1
        if (workDone + (pingService?.workDone ?: 0) >= totalWork) {
            worker.shutdown()
        } else {
            pingWriter!!.addAsync(1)
        }
        return true
    }
}

class _PingPongPipeline(totalWork: Long) : CoroutineWorker() {
    override val logger = KotlinLogging.logger {}

    override fun processKey(key: SelectionKey) {}

    val pingService = PingService(this, totalWork)
    val pongService = PongService(this, totalWork)

    override val services = listOf(
            pingService,
            pongService
    )

    override fun onStart(){
        pingService.pongService = pongService
        pongService.pingService = pingService
        while (pingService.pongWriter!!.offer(1)) {
        }
    }
}

class _ServiceTests : CDBTest() {
    @Test
    fun pipelinesShouldStartAndStop() {
        repeat(0) {
            val pipeline = _EmptyPipeline()

            assertTrue(pipeline.isRunning)
            pipeline.start()
            eventually { assertTrue(pipeline.isRunning) }
            pipeline.shutdown()
            eventually { assertFalse(pipeline.isRunning) }
        }
    }

    @Test
    fun pipelinesShouldRunSuccessfully() {
        repeat(0) {
            val workCount = 1000000L
            val pipeline = _CountdownPipeline(workCount)

            val pre = System.currentTimeMillis()
            pipeline.start()
            eventually { assertEquals(workCount, pipeline.countdownService.workDone) }

            val post = System.currentTimeMillis()
            logger.info("Took ${post - pre}ms for $workCount, ${(workCount / (post - pre)) / 1000.0} million per second")
        }
    }

    @Test
    fun pipelinesShouldRescheduleBetweenServices() {
        repeat(16) {
            val workCount = 1000000L
            val pipeline = _PingPongPipeline(workCount)

            val pre = System.currentTimeMillis()
            pipeline.start()
            eventually { assertEquals(workCount, (pipeline.pingService.workDone + pipeline.pongService.workDone)) }
            val post = System.currentTimeMillis()
            logger.info("A Took ${post - pre}ms for $workCount, ${(workCount / (post - pre)) / 1000.0} million per second")
            pipeline.shutdown()
        }
    }
}
*/
