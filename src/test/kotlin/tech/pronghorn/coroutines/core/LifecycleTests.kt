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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.test.PronghornTest
import tech.pronghorn.test.lightRepeatCount
import java.util.concurrent.atomic.AtomicBoolean

class HasLifecycle : Lifecycle() {
    var onStartCalled = false
    var onShutdownCalled = false
    var onLifecycleStartCalled = false
    var onLifecycleShutdownCalled = false

    override fun onStart() {
        onStartCalled = true
    }

    override fun onShutdown() {
        onShutdownCalled = true
    }

    override fun onLifecycleStart() {
        onLifecycleStartCalled = true
    }

    override fun onLifecycleShutdown() {
        onLifecycleShutdownCalled = true
    }

    fun start() = lifecycleStart()

    fun shutdown() = lifecycleShutdown()
}

class HasLifecycleWithShutdownExceptions : Lifecycle() {
    var onShutdownCalled = false
    var onLifecycleShutdownCalled = false

    override fun onShutdown() {
        onShutdownCalled = true
        throw Exception()
    }

    override fun onLifecycleShutdown() {
        onLifecycleShutdownCalled = true
        throw Exception()
    }

    fun start() = lifecycleStart()

    fun shutdown() = lifecycleShutdown()
}

class LifecycleTests : PronghornTest() {
    @RepeatedTest(lightRepeatCount)
    fun lifecycleTest() {
        val hasLifecycle = HasLifecycle()
        assertTrue(hasLifecycle.isInitialized())
        assertFalse(hasLifecycle.isRunning())
        assertFalse(hasLifecycle.isShutdown())
        assertFalse(hasLifecycle.onLifecycleStartCalled)
        assertFalse(hasLifecycle.onStartCalled)
        assertFalse(hasLifecycle.onLifecycleShutdownCalled)
        assertFalse(hasLifecycle.onShutdownCalled)

        hasLifecycle.start()
        assertFalse(hasLifecycle.isInitialized())
        assertTrue(hasLifecycle.isRunning())
        assertFalse(hasLifecycle.isShutdown())
        assertTrue(hasLifecycle.onLifecycleStartCalled)
        assertTrue(hasLifecycle.onStartCalled)
        assertFalse(hasLifecycle.onLifecycleShutdownCalled)
        assertFalse(hasLifecycle.onShutdownCalled)

        hasLifecycle.shutdown()
        assertFalse(hasLifecycle.isInitialized())
        assertFalse(hasLifecycle.isRunning())
        assertTrue(hasLifecycle.isShutdown())
        assertTrue(hasLifecycle.onLifecycleStartCalled)
        assertTrue(hasLifecycle.onStartCalled)
        assertTrue(hasLifecycle.onLifecycleShutdownCalled)
        assertTrue(hasLifecycle.onShutdownCalled)
    }

    @RepeatedTest(lightRepeatCount)
    fun shutdownMethodsTest() {
        val hasLifecycle = HasLifecycle()
        hasLifecycle.start()

        val firstFinished = AtomicBoolean(false)
        val secondFinished = AtomicBoolean(false)
        hasLifecycle.registerShutdownMethod { firstFinished.set(true) }
        hasLifecycle.registerShutdownMethod { secondFinished.set(true) }

        assertFalse(firstFinished.get())
        assertFalse(secondFinished.get())

        hasLifecycle.shutdown()

        assertTrue(firstFinished.get())
        assertTrue(secondFinished.get())
    }

    /* The same as above tests, but every event throws an exception.
     * Ensures exceptions are isolated and startup and shutdown continues.
     */
    @RepeatedTest(lightRepeatCount)
    fun shutdownWithExceptionsTest() {
        val withExceptions = HasLifecycleWithShutdownExceptions()

        var firstFinished = false
        var secondFinished = false
        withExceptions.registerShutdownMethod {
            firstFinished = true
            throw Exception()
        }
        withExceptions.registerShutdownMethod {
            secondFinished = true
            throw Exception()
        }

        assertFalse(firstFinished)
        assertFalse(secondFinished)

        assertFalse(withExceptions.onLifecycleShutdownCalled)
        assertFalse(withExceptions.onShutdownCalled)

        withExceptions.start()
        assertFalse(withExceptions.onLifecycleShutdownCalled)
        assertFalse(withExceptions.onShutdownCalled)

        withExceptions.shutdown()
        assertFalse(withExceptions.isInitialized())
        assertFalse(withExceptions.isRunning())
        assertTrue(withExceptions.isShutdown())
        assertTrue(withExceptions.onLifecycleShutdownCalled)
        assertTrue(withExceptions.onShutdownCalled)
        assertTrue(firstFinished)
        assertTrue(secondFinished)
    }
}
