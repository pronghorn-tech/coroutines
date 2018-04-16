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

package tech.pronghorn.coroutines

import org.junit.jupiter.api.AfterEach
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.plugins.internalQueue.InternalQueuePlugin
import tech.pronghorn.test.PronghornTest

abstract class PronghornTestWithWorkerCleanup: PronghornTest() {
    val testWorkers = InternalQueuePlugin.getUnbounded<CoroutineWorker>()

    @AfterEach
    fun shutdownWorkers() {
        var worker = testWorkers.poll()
        while(worker != null){
            if(worker.isRunning()){
                worker.shutdown()
            }
            worker = testWorkers.poll()
        }
    }

    fun getWorker(start: Boolean = true): CoroutineWorker {
        val worker = CoroutineWorker()
        testWorkers.add(worker)
        if(start){
            worker.start()
        }
        return worker
    }

    fun <T : CoroutineWorker> getWorker(start: Boolean = true,
                                        block: () -> T): T {
        val worker = block()
        testWorkers.add(worker)
        if(start){
            worker.start()
        }
        return worker
    }
}
