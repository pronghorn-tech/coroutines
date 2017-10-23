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

package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.*
import tech.pronghorn.coroutines.awaitable.queue.*
import tech.pronghorn.util.stackTraceToString

abstract class ExternalQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = ExternalQueue(queueCapacity, this)

    protected val queueReader: ExternalQueue.Reader<WorkType> = queue.queueReader

    fun getQueueWriter(): ExternalQueue.Writer<WorkType> = queue.queueWriter

    abstract suspend fun process(work: WorkType)

    override suspend fun run() {
        while (isRunning()) {
            val workItem = await(queueReader)
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
