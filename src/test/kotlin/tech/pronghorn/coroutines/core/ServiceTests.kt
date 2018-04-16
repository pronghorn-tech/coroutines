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

package tech.pronghorn.coroutines.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.PronghornTestWithWorkerCleanup
import tech.pronghorn.coroutines.services.InternalQueueService
import tech.pronghorn.test.eventually
import tech.pronghorn.test.heavyRepeatCount
import java.time.Duration

class CountdownWorker(totalWork: Long) : CoroutineWorker() {
    val countdownService = CountdownService(this, totalWork)

    override val initialServices = listOf(countdownService)

    override fun onStart() {
        val countdownWriter = countdownService.getQueueWriter()
        countdownWriter.offer(1)
    }
}

class CountdownService(override val worker: CoroutineWorker,
                       val totalWork: Long) : InternalQueueService<Int>() {
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

class PingService(override val worker: CoroutineWorker,
                  val totalWork: Long) : InternalQueueService<Int>() {
    var pongService: PongService? = null

    var workDone = 0L

    val pongWriter by lazy(LazyThreadSafetyMode.NONE) {
        pongService?.getQueueWriter()
    }

    override fun shouldYield(): Boolean = true

    fun shutdownWorker() {
        worker.shutdown()
    }

    @Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
    override suspend inline fun process(work: Int): Boolean {
        if (workDone + (pongService?.workDone ?: 0) >= totalWork) {
            if(worker.isRunning()) {
                shutdownWorker()
            }
        }
        else {
            workDone += 1
            pongWriter!!.offer(1) || pongWriter!!.addAsync(1)
        }
        return true
    }
}

class PongService(override val worker: CoroutineWorker,
                  val totalWork: Long) : InternalQueueService<Int>() {
    var pingService: PingService? = null

    val pingWriter by lazy(LazyThreadSafetyMode.NONE) {
        pingService?.getQueueWriter()
    }
    var workDone = 0L

    override fun shouldYield(): Boolean = true

    fun shutdownWorker() {
        worker.shutdown()
    }

    @Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
    override suspend inline fun process(work: Int): Boolean {
        if (workDone + (pingService?.workDone ?: 0) >= totalWork) {
            if(worker.isRunning()) {
                shutdownWorker()
            }
        }
        else {
            workDone += 1
            pingWriter!!.offer(1) || pingWriter!!.addAsync(1)
        }
        return true
    }
}

class PingPongWorker(totalWork: Long) : CoroutineWorker() {
    val pingService = PingService(this, totalWork)
    val pongService = PongService(this, totalWork)

    override val initialServices = listOf(pingService, pongService)

    override fun onStart() {
        pingService.pongService = pongService
        pongService.pingService = pingService
        while (pingService.pongWriter!!.offer(1)) {
        }
    }
}

class ServiceTests : PronghornTestWithWorkerCleanup() {
    @RepeatedTest(heavyRepeatCount)
    fun workersShouldRunSuccessfullyTest() {
        val workCount = 1000000L
        val worker = getWorker(false) { CountdownWorker(workCount) }

        val pre = System.currentTimeMillis()
        worker.start()
        eventually { assertEquals(workCount, worker.countdownService.workDone) }
        val post = System.currentTimeMillis()
        logger.info { "Took ${post - pre}ms for $workCount, ${(workCount / (post - pre)) / 1000.0} million per second" }
    }

    @RepeatedTest(64)
    fun pipelinesShouldRescheduleBetweenServicesTest() {
        val workCount = 1000000L
        val worker = getWorker(false) { PingPongWorker(workCount) }

        val pre = System.currentTimeMillis()
        worker.start()
        eventually(Duration.ofSeconds(60)) { assertEquals(workCount, (worker.pingService.workDone + worker.pongService.workDone)) }
        val post = System.currentTimeMillis()
        logger.info { "A Took ${post - pre}ms for $workCount, ${(workCount / (post - pre)) / 1000.0} million per second" }
    }
}
