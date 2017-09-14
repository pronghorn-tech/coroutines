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

import tech.pronghorn.coroutines.awaitable.QueueWriter

/**
 * Calls process() for each work item in the queue.
 * Actual implementations may or not have mechanisms for cross thread access.
 */
abstract class QueueService<WorkType> : Service() {
    /**
     * Provides QueueWriters to external users that need to append to this service's queue.
     * Implementations may limit access to a writer.
     */
    abstract fun getQueueWriter(): QueueWriter<WorkType>

    /**
     * Allows a service to decide when yielding to the worker happens by returning true when yielding is desired.
     */
    open fun shouldYield(): Boolean = false
}
