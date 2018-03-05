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

package tech.pronghorn.coroutines.services

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.PronghornTestWithWorkerCleanup
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.test.eventually
import tech.pronghorn.test.lightRepeatCount
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class ExternalQueueTestService(override val worker: CoroutineWorker) : ExternalQueueService<Int>(worker, 4096) {
    @Volatile
    var processed = 0L

    override suspend fun process(work: Int) {
        processed += work
    }
}

class ExternalQueueServiceTests : PronghornTestWithWorkerCleanup() {
    @RepeatedTest(lightRepeatCount)
    fun stressTest() {
        val worker = getWorker()
        val otherWorker = getWorker()

        val service = ExternalQueueTestService(worker)
        worker.addService(service)
        val writer = service.getQueueWriter()

        var expected = 0L
        val done = AtomicBoolean(false)
        val workCount = 1000000

        val pre = System.currentTimeMillis()
        otherWorker.executeInWorker {
            otherWorker.launchWorkerCoroutine {
                var x = 0
                while (x < workCount) {
                    if(!writer.offer(x)) {
                        writer.addAsync(x)
                    }
                    expected += x
                    x += 1
                }
                done.set(true)
            }
        }

        eventually(Duration.ofSeconds(20)) {
            assertTrue(done.get())
            assertEquals(expected, service.processed)
        }
        val post = System.currentTimeMillis()
        logger.info { "Took ${post - pre}ms for $workCount, ${(workCount / (post - pre)) / 1000.0} million per second" }
    }
}
