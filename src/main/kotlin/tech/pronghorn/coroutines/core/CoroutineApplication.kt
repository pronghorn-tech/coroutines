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

import tech.pronghorn.plugins.concurrentSet.ConcurrentSetPlugin
import tech.pronghorn.plugins.logging.LoggingPlugin

abstract class CoroutineApplication<T : CoroutineWorker> {
    abstract protected val workerCount: Int
    protected val logger = LoggingPlugin.get(javaClass)
    protected val workers = ConcurrentSetPlugin.get<T>()

    var isRunning = false
        private set

    abstract protected fun spawnWorker(): T

    open fun onStart() = Unit

    open fun onShutdown() = Unit

    fun start() {
        for (x in 1..workerCount) {
            val worker = spawnWorker()
            workers.add(worker)
        }
        isRunning = true
        onStart()
        workers.forEach(CoroutineWorker::start)
    }

    fun shutdown() {
        isRunning = false
        onShutdown()
        try {
            workers.forEach(CoroutineWorker::shutdown)
        }
        catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
