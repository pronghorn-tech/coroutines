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

import tech.pronghorn.plugins.concurrentMap.ConcurrentMapPlugin

public abstract class Attachable {
    private val attachments = ConcurrentMapPlugin.get<AttachmentKey<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getAttachment(key: AttachmentKey<T>): T? {
        val value = attachments[key] ?: return null
        return value as T
    }

    public fun <T : Any> putAttachment(key: AttachmentKey<T>,
                                value: T): Boolean {
        return attachments.putIfAbsent(key, value) == null
    }

    public fun <T : Any> removeAttachment(key: AttachmentKey<T>): Boolean {
        return attachments.remove(key) != null
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getOrPutAttachment(key: AttachmentKey<T>,
                                     default: () -> T): T {
        return attachments.getOrPut(key, default) as T
    }
}
