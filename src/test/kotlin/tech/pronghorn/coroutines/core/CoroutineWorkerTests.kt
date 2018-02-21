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

package tech.pronghorn.coroutines.core

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import tech.pronghorn.test.*

class DummyService(override val worker: CoroutineWorker) : Service() {
    suspend override fun run() {
        while(isRunning()) {
            yieldAsync()
        }
    }
}

class CoroutineWorkerTests : PronghornTest() {
    var testWorker: CoroutineWorker? = null

    @AfterEach
    fun shutdownTestWorker() {
        val worker = testWorker
        testWorker = null
        if(worker != null && worker.isRunning()){
            worker.shutdown()
        }
    }

    @Test
    fun isWorkerThreadTestTest() {
        val worker = CoroutineWorker()

        assertFalse(worker.isWorkerThread())
        var fromService: Boolean? = null

        val service = object : Service() {
            override val worker = worker
            suspend override fun run() {
                fromService = worker.isWorkerThread()
            }
        }

        worker.addService(service)
        worker.start()
        eventually {
            assertNotNull(fromService)
            assertTrue(fromService!!)
        }
        worker.shutdown()
    }

    @RepeatedTest(heavyRepeatCount)
    fun initialServicesTest() {
        val worker = object: CoroutineWorker() {
            val serviceA = DummyService(this)
            val serviceB = DummyService(this)

            override val initialServices: List<Service> = listOf(serviceA, serviceB)
        }
        testWorker = worker
        worker.start()

        eventually {
            assertTrue(worker.getRunningServices().contains(worker.serviceA))
            assertTrue(worker.getRunningServices().contains(worker.serviceB))
        }
    }

    @RepeatedTest(heavyRepeatCount)
    fun addServicesTest() {
        val worker = CoroutineWorker()
        testWorker = worker
        worker.start()

        assertTrue(worker.getRunningServices().isEmpty())
        val dummyService = DummyService(worker)
        worker.addService(dummyService)

        eventually {
            assertTrue(worker.getRunningServices().contains(dummyService))
        }
    }

    @RepeatedTest(heavyRepeatCount)
    fun disallowCrossWorkerServiceTest() {
        val workerA = CoroutineWorker()
        val workerB = CoroutineWorker()
        val workerAService = DummyService(workerA)

        assertThrows(IllegalStateException::class.java, { workerB.addService(workerAService) })
    }

    @RepeatedTest(heavyRepeatCount)
    fun executeInWorkerTest() {
        val worker = CoroutineWorker()
        testWorker = worker
        worker.start()

        var x = 0
        val toSend = 16
        var expected = 0
        var processed = 0
        while(x < toSend){
            val sendValue = 1 + random.nextInt(1024)
            worker.executeInWorker {
                processed += sendValue
            }
            expected += sendValue

            eventually {
                assertEquals(expected, processed)
            }
            x += 1
        }
    }

    @Test
    fun threadLocalWorkerTest() {
        val worker = CoroutineWorker()
        worker.start()

        var workerThread: CoroutineWorker? = null
        worker.executeInWorker {
            workerThread = ThreadLocalWorker.get()
        }

        eventually {
            assertEquals(workerThread, worker)
        }
        worker.shutdown()
    }
}
