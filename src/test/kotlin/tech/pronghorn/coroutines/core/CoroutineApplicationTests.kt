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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.test.*

class CoroutineApplicationTests : PronghornTest() {
    class TestApplication : CoroutineApplication() {
        var onStartCalled = false
        var onShutdownCalled = false
        val workerA = CoroutineWorker()
        val workerB = CoroutineWorker()
        override val workers = setOf(workerA, workerB)

        override fun onStart() {
            onStartCalled = true
        }

        override fun onShutdown() {
            onShutdownCalled = true
        }
    }

    class TestService(override val worker: CoroutineWorker) : Service() {
        suspend override fun run() = Unit
    }

    var testApplication: TestApplication? = null

    @AfterEach
    fun shutdownTestApplication() {
        val application = testApplication
        testApplication = null
        if(application != null && application.isRunning()){
            application.shutdown()
        }
    }

    fun getApplication(): TestApplication {
        val application = TestApplication()
        testApplication = application
        return application
    }

    @RepeatedTest(lightRepeatCount)
    fun isRunningTest() {
        val application = getApplication()
        assertFalse(application.isRunning())
        application.start()
        assertTrue(application.isRunning())
        application.shutdown()
        assertFalse(application.isRunning())
    }

    @RepeatedTest(lightRepeatCount)
    fun onStartTest() {
        val application = getApplication()
        assertFalse(application.onStartCalled)
        application.start()
        assertTrue(application.onStartCalled)
        application.shutdown()
    }

    @RepeatedTest(lightRepeatCount)
    fun onShutdownTest() {
        val application = getApplication()
        application.start()
        assertFalse(application.onShutdownCalled)
        application.shutdown()
        assertTrue(application.onShutdownCalled)
    }

    @RepeatedTest(lightRepeatCount)
    fun workersRunningTest() {
        val application = getApplication()
        assertFalse(application.workerA.isRunning())
        assertFalse(application.workerB.isRunning())
        application.start()
        assertTrue(application.workerA.isRunning())
        assertTrue(application.workerB.isRunning())
        application.shutdown()
        assertFalse(application.workerA.isRunning())
        assertFalse(application.workerB.isRunning())
    }

    @RepeatedTest(lightRepeatCount)
    fun servicesStartedTest() {
        val application = getApplication()
        application.addService { worker -> TestService(worker) }
        application.start()
        eventually {
            assertNotNull(application.workerA.getService<TestService>())
            assertNotNull(application.workerB.getService<TestService>())
        }
        val serviceA = application.workerA.getService<TestService>()!!
        val serviceB = application.workerB.getService<TestService>()!!
        assertTrue(serviceA.isRunning())
        assertTrue(serviceB.isRunning())
        application.shutdown()
        assertFalse(serviceA.isRunning())
        assertFalse(serviceB.isRunning())
    }
}
