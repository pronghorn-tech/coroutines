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

import tech.pronghorn.coroutines.awaitable.queue.InternalQueue
import tech.pronghorn.coroutines.core.Service
import tech.pronghorn.util.stackTraceToString

/**
 * This type of service is strictly for use within a single worker. process() is allowed to be
 * partially completed for an individual work item, indicated by the Boolean response from process().
 * In the event that a work item is partially processed, it is re-added immediately to the end of the queue.
 */
public abstract class InternalQueueService<WorkType>(queueCapacity: Int = 1024) : Service() {
    private val queue = InternalQueue<WorkType>(queueCapacity)
    private val queueReader = queue.reader

    public fun getQueueWriter(): InternalQueue.Writer<WorkType> = queue.writer

    abstract suspend fun process(work: WorkType): Boolean

    /**
     * Allows a service to decide when yielding to the worker happens by returning true when yielding is desired.
     */
    protected open fun shouldYield(): Boolean = false

    final override suspend fun run() {
        var workItem = queueReader.poll() ?: queueReader.awaitAsync()
        while (isRunning()) {
            val finished = try {
                process(workItem)
            }
            catch (ex: Exception) {
                logger.error { "Queue service threw exception: ${ex.stackTraceToString()}" }
                true
            }

            if (!finished) {
                val prevWorkItem = workItem
                workItem = queueReader.pollAndAdd(workItem)
                if (workItem == prevWorkItem) {
                    yieldAsync()
                }
            }
            else {
                workItem = queueReader.poll() ?: queueReader.awaitAsync()
            }

            if (shouldYield()) {
                yieldAsync()
            }
        }
    }
}
