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

import tech.pronghorn.coroutines.awaitable.ServiceCoroutineContext
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine

fun <T> launchServiceCoroutine(context: CoroutineContext,
                               block: suspend () -> T) {
    if (context is ServiceCoroutineContext) {
        val coroutine = ServiceCoroutine<T>(context)
        block.startCoroutine(coroutine)
    }
    else {
        throw Exception("Illegal coroutine context, service coroutines must be launched within a service context.")
    }
}
