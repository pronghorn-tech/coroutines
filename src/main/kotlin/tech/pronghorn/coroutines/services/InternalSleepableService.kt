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
import tech.pronghorn.coroutines.awaitable.future.CoroutineFuture
import tech.pronghorn.coroutines.awaitable.future.CoroutinePromise

abstract class InternalSleepableService : Service() {
    private var sleepPromise: CoroutinePromise<Unit>? = null

    suspend fun sleepAsync() {
        val future = CoroutineFuture<Unit>()
        sleepPromise = future.promise()
        await(future)
    }

    fun wake() {
        sleepPromise?.complete(Unit)
        sleepPromise = null
    }
}
