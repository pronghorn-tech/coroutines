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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.coroutines.PronghornTestWithWorkerCleanup
import tech.pronghorn.test.eventually
import tech.pronghorn.test.heavyRepeatCount
import java.nio.ByteBuffer
import java.nio.channels.Pipe
import java.nio.channels.SelectionKey
import java.util.Arrays
import java.util.Random

class SelectionKeyHandlerTester(val worker: CoroutineWorker,
                                val random: Random) {
    val bufferSize = 64 + random.nextInt(1024)
    val pipe = Pipe.open()
    val sink = pipe.sink()
    val source = pipe.source()

    init {
        sink.configureBlocking(false)
        source.configureBlocking(false)
    }

    val readBuffer = ByteBuffer.allocate(bufferSize)
    val readBytes = ByteArray(bufferSize)

    val writeBuffer = ByteBuffer.allocate(bufferSize)
    val randomBytes = ByteArray(bufferSize)

    @Volatile
    var written = false

    fun reset() {
        written = false
        readBuffer.clear()
        Arrays.fill(readBytes, 0)
        writeBuffer.clear()
        Arrays.fill(randomBytes, 0)
    }

    fun handleRead() {
        source.read(readBuffer)
        readBuffer.flip()
        readBuffer.get(readBytes)
    }

    fun testRead(handler: SelectionKeyHandler) {
        reset()
        worker.executeInWorker {
            worker.registerSelectionKeyHandler(source, handler, SelectionKey.OP_READ)
        }

        val writeBuffer = ByteBuffer.allocate(bufferSize)
        val randomBytes = ByteArray(bufferSize)
        random.nextBytes(randomBytes)
        writeBuffer.put(randomBytes)
        writeBuffer.flip()
        sink.write(writeBuffer)

        eventually {
            assertTrue(Arrays.equals(readBytes, randomBytes))
        }
    }

    fun handleWrite() {
        written = true
    }

    fun testWrite(handler: SelectionKeyHandler) {
        reset()
        worker.executeInWorker {
            worker.registerSelectionKeyHandler(sink, handler, SelectionKey.OP_WRITE)
        }
        eventually {
            written = true
        }
    }
}

class SelectionKeyHandlerTests : PronghornTestWithWorkerCleanup() {
    @RepeatedTest(heavyRepeatCount)
    fun readSelectionKeyHandlerTest() {
        val worker = getWorker()

        val tester = SelectionKeyHandlerTester(worker, random)
        val handler = object : ReadSelectionKeyHandler {
            override fun handleReadable() = tester.handleRead()
        }

        tester.testRead(handler)
    }

    @RepeatedTest(heavyRepeatCount)
    fun writeSelectionKeyHandlerTest() {
        val worker = getWorker()

        val tester = SelectionKeyHandlerTester(worker, random)
        val handler = object : WriteSelectionKeyHandler {
            override fun handleWritable() = tester.handleWrite()
        }

        tester.testWrite(handler)
    }

    @RepeatedTest(heavyRepeatCount)
    fun readHandlerPipeTest() {
        val worker = getWorker()

        val pipe = Pipe.open()
        val sink = pipe.sink()
        val source = pipe.source()
        sink.configureBlocking(false)
        source.configureBlocking(false)

        val bufferSize = 64 + random.nextInt(1024)

        val readHandler = object : ReadSelectionKeyHandler {
            val readBuffer = ByteBuffer.allocate(bufferSize)
            val readBytes = ByteArray(bufferSize)

            override fun handleReadable() {
                source.read(readBuffer)
                readBuffer.flip()
                readBuffer.get(readBytes)
            }
        }

        worker.executeInWorker {
            worker.registerSelectionKeyHandler(source, readHandler, SelectionKey.OP_READ)
        }

        val writeBuffer = ByteBuffer.allocate(bufferSize)
        val randomBytes = ByteArray(bufferSize)
        random.nextBytes(randomBytes)
        writeBuffer.put(randomBytes)
        writeBuffer.flip()
        sink.write(writeBuffer)

        eventually {
            assertTrue(Arrays.equals(readHandler.readBytes, randomBytes))
        }
    }

    @RepeatedTest(heavyRepeatCount)
    fun writeHandlerPipeTest() {
        val worker = getWorker()

        val pipe = Pipe.open()
        val sink = pipe.sink()
        val source = pipe.source()
        sink.configureBlocking(false)
        source.configureBlocking(false)

        val writeHandler = object : WriteSelectionKeyHandler {
            var written = false

            override fun handleWritable() {
                written = true
            }
        }

        worker.executeInWorker {
            worker.registerSelectionKeyHandler(sink, writeHandler, SelectionKey.OP_WRITE)
        }
        eventually {
            writeHandler.written = true
        }
    }

    @RepeatedTest(heavyRepeatCount)
    fun readWriteHandlerPipeTest() {
        val worker = getWorker()

        val pipe = Pipe.open()
        val sink = pipe.sink()
        val source = pipe.source()
        sink.configureBlocking(false)
        source.configureBlocking(false)

        val writeHandler = object : ReadWriteSelectionKeyHandler {
            var written = false

            override fun handleWritable() {
                written = true
            }

            override fun handleReadable() {

            }
        }

        worker.executeInWorker {
            worker.registerSelectionKeyHandler(sink, writeHandler, SelectionKey.OP_WRITE)
        }
        eventually {
            writeHandler.written = true
        }
    }
}
