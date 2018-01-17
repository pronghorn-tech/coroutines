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

package tech.pronghorn.coroutines.services

import tech.pronghorn.coroutines.awaitable.*
import tech.pronghorn.coroutines.awaitable.queue.*
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.core.Service
import tech.pronghorn.util.stackTraceToString

public abstract class ExternalQueueService<WorkType>(worker: CoroutineWorker,
                                                     queueCapacity: Int = 4096) : Service() {
    private val queue = ExternalQueue<WorkType>(queueCapacity, worker)
    protected val queueReader: ExternalQueue.Reader<WorkType> = queue.reader

    public fun getQueueWriter(): ExternalQueue.Writer<WorkType> = queue.writer

    abstract suspend fun process(work: WorkType)

    /**
     * Allows a service to decide when yielding to the worker happens by returning true when yielding is desired.
     */
    protected open fun shouldYield(): Boolean = false

    final override suspend fun run() {
        while (isRunning()) {
            val workItem = queueReader.poll() ?: await(queueReader)
            if (shouldYield()) {
                yieldAsync()
            }

            try {
                process(workItem)
            }
            catch (ex: Exception) {
                logger.error { "Queue service threw exception: ${ex.stackTraceToString()}" }
            }
        }
    }
}
