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

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineUninterceptedOrReturn

@Suppress("NOTHING_TO_INLINE")
suspend inline fun <T> suspendCoroutine(crossinline block: (Continuation<T>) -> Any): T {
    if(DEBUG) {
        return suspendCoroutineUninterceptedOrReturn { continuation: Continuation<T> ->
            val context = continuation.context
            if (context is ServiceCoroutineContext) {
                context.service.suspendLocation = Thread.currentThread().stackTrace
            }
            block(continuation)
        }
    }
    else {
        return suspendCoroutineUninterceptedOrReturn { continuation: Continuation<T> ->
            (continuation.context as PronghornCoroutineContext).onSuspend()
            block(continuation)
        }
    }
}
