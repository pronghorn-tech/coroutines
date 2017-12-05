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

import tech.pronghorn.plugins.logging.LoggingPlugin

abstract class CoroutineApplication : Lifecycle() {
    protected val logger = LoggingPlugin.get(javaClass)
    protected abstract val workers: Set<CoroutineWorker>

    public fun addService(generator: (CoroutineWorker) -> Service) {
        workers.forEach { worker ->
            worker.addService(generator(worker))
        }
    }

    public fun start() = lifecycleStart()

    public fun shutdown() = lifecycleShutdown()

    final override fun onLifecycleStart() {
        workers.forEach(CoroutineWorker::start)
    }

    final override fun onLifecycleShutdown() {
        workers.forEach { worker ->
            try {
                if (worker.isRunning()) {
                    worker.shutdown()
                }
            }
            catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
