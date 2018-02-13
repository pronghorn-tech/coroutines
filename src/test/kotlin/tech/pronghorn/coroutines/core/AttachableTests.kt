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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.test.PronghornTest
import tech.pronghorn.test.heavyRepeatCount

class AttachableTests: PronghornTest() {
    @RepeatedTest(heavyRepeatCount)
    fun attachmentSupportTest() {
        val worker = CoroutineWorker()
        val attachment = "attachment"
        val key = object : AttachmentKey<String> {}

        Assertions.assertNull(worker.getAttachment(key))
        Assertions.assertTrue(worker.putAttachment(key, attachment))
        Assertions.assertFalse(worker.putAttachment(key, attachment))
        Assertions.assertEquals(attachment, worker.getAttachment(key))
        Assertions.assertTrue(worker.removeAttachment(key))
        Assertions.assertFalse(worker.removeAttachment(key))
        Assertions.assertNull(worker.getAttachment(key))
        Assertions.assertEquals(attachment, worker.getOrPutAttachment(key, { attachment }))
        Assertions.assertEquals(attachment, worker.getAttachment(key))
    }
}
