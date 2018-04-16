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

package tech.pronghorn.coroutines.awaitable.queue

import tech.pronghorn.plugins.logging.LoggingPlugin
import tech.pronghorn.util.isPowerOfTwo
import tech.pronghorn.util.roundToNextPowerOfTwo

private object QueueUtils {
    private val logger = LoggingPlugin.get(javaClass)
    internal fun validateQueueCapacity(requested: Int): Int {
        if (requested < 4) {
            logger.warn { "Queue size must be at least four, using queue capacity of four." }
            return 4
        }
        else if (!isPowerOfTwo(requested)) {
            val rounded = roundToNextPowerOfTwo(requested)
            logger.warn { "Queue sizes must be powers of two, rounding $requested to next power of two : $rounded" }
            return rounded
        }

        return requested
    }
}

internal fun validateQueueCapacity(requested: Int): Int = QueueUtils.validateQueueCapacity(requested)
