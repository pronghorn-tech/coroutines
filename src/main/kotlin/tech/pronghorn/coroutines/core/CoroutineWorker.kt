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

import tech.pronghorn.coroutines.awaitable.InternalFuture
import tech.pronghorn.coroutines.awaitable.QueueWriter
import tech.pronghorn.coroutines.messages.AddServiceMessage
import tech.pronghorn.coroutines.messages.PromiseCompletionMessage
import tech.pronghorn.coroutines.service.*
import tech.pronghorn.plugins.internalQueue.InternalQueuePlugin
import tech.pronghorn.plugins.logging.LoggingPlugin
import tech.pronghorn.plugins.mpscQueue.MpscQueuePlugin
import tech.pronghorn.util.ignoreExceptions
import java.nio.channels.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.coroutines.experimental.RestrictsSuspension

private val workerIDs = AtomicLong(0)

/**
 * Runs process() for each SelectionKey triggered by its Selector
 * This happens on A dedicated thread that runs when start() is called
 */
@RestrictsSuspension
abstract class CoroutineWorker {
    val workerID = workerIDs.incrementAndGet()
    protected val logger = LoggingPlugin.get(javaClass)
    protected val selector: Selector = Selector.open()
    private val workerThread = thread(start = false, name = "${javaClass.simpleName}-$workerID") {
        startInternal()
    }
    private val runningServices: MutableList<Service> = mutableListOf()
    private val intervalServices: MutableList<IntervalService> = mutableListOf()
    private var nextTimedServiceTime: Long? = null
    private val runQueue = MpscQueuePlugin.getUnbounded<Service>()
    private val interWorkerMessages = MpscQueuePlugin.getUnbounded<Any>()
    private val shutdownMethods = InternalQueuePlugin.getUnbounded<() -> Unit>()
    private val startedLock = ReentrantLock()
    private val startedCondition = startedLock.newCondition()
    private val attachments = mutableMapOf<WorkerAttachmentKey<*>, Any>()
    @Volatile private var hasInterWorkerMessages = false
    @Volatile private var started = false
    var isRunning = false
        private set

    abstract protected val services: List<Service>

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAttachment(key: WorkerAttachmentKey<T>): T? {
        val value = attachments[key] ?: return null
        return value as T
    }

    fun <T : Any> putAttachment(key: WorkerAttachmentKey<T>,
                                value: T): Boolean {
        return attachments.putIfAbsent(key, value) == null
    }

    fun <T : Any> removeAttachment(key: WorkerAttachmentKey<T>): Boolean {
        return attachments.remove(key) != null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPutAttachment(key: WorkerAttachmentKey<T>,
                                     default: () -> T): T {
        return attachments.getOrPut(key, default) as T
    }

    fun registerShutdownMethod(method: () -> Unit) {
        shutdownMethods.add(method)
    }

    internal fun offerReady(service: Service) {
        if (!runQueue.offer(service)) {
            throw Exception("Unexpectedly failed to enqueue service.")
        }
    }

    fun isWorkerThread() = Thread.currentThread() == workerThread

    fun sendInterWorkerMessage(message: Any) {
        interWorkerMessages.add(message)
        hasInterWorkerMessages = true
        selector.wakeup()
    }

    internal fun <T> crossThreadCompletePromise(promise: InternalFuture.InternalPromise<T>,
                                                value: T) {
        sendInterWorkerMessage(PromiseCompletionMessage(promise, value))
    }

    internal fun addService(service: Service) {
        if (isWorkerThread()) {
            startService(service)
        }
        else {
            sendInterWorkerMessage(AddServiceMessage(service))
        }
    }

    fun getRunningServices(): List<Service> = runningServices.toList()

    inline fun <reified ServiceType : Service> getService(): ServiceType? {
        getRunningServices().forEach { service ->
            if (service is ServiceType) {
                return service
            }
        }
        return null
    }

    inline fun <reified WorkType, reified ServiceType : QueueService<WorkType>> getServiceQueueWriter(): QueueWriter<WorkType>? {
        getRunningServices().forEach { service ->
            if (service is ServiceType) {
                return service.getQueueWriter()
            }
        }
        return null
    }

    open protected fun onShutdown() = Unit

    open protected fun onStart() = Unit

    private fun startService(service: Service) {
        service.start()
        runningServices.add(service)
    }

    private fun startInternal() {
        startedLock.lock()
        try {
            onStart()
            services.forEach { service -> startService(service) }
            isRunning = true
        }
        finally {
            started = true
            startedCondition.signal()
            startedLock.unlock()
        }

        run()
    }

    fun start() {
        startedLock.lock()
        try {
            workerThread.start()
            while (!started) {
                startedCondition.await()
            }
        }
        finally {
            startedLock.unlock()
        }
    }

    private fun shutdownInternal() {
        shutdownMethods.forEach { it() }
        onShutdown()
        try {
            selector.close()
        }
        finally {
            isRunning = false
        }
    }

    internal fun shutdown() {
        logger.debug { "$workerID Requesting shutdown" }
        ignoreExceptions(
                { runQueue.clear() },
                { runningServices.forEach(Service::shutdown) },
                { selector.close() },
                { workerThread.interrupt() },
                { workerThread.join() }
        )

    }

    private fun runService(service: Service) {
        service.isQueued = false
        service.resume()
    }

    private fun runTimedServices() {
        if (nextTimedServiceTime == null) {
            return
        }

        val nextTime = nextTimedServiceTime
        val now = System.currentTimeMillis()
        if (nextTime != null && now >= nextTime) {
            intervalServices.map { service ->
                if (now >= service.nextRunTime) {
                    service.wake()
                }
            }

            nextTimedServiceTime = calculateNextTimedServiceTime()
        }
    }

    private fun calculateNextTimedServiceTime(): Long? {
        if (intervalServices.isEmpty()) {
            return null
        }

        return intervalServices.map { it.nextRunTime }.min()
    }

    private fun calculateSelectTimeout(): Long? {
        val nextTime = nextTimedServiceTime
        if (nextTime == null) {
            return null
        }
        else {
            return nextTime - System.currentTimeMillis()
        }
    }

    private fun run() {
        logger.debug { "$workerID worker.run()" }
        nextTimedServiceTime = calculateNextTimedServiceTime()
        while (true) {
            try {
                var runnable = runQueue.poll()
                if (runnable != null) {
                    while (runnable != null) {
                        runService(runnable)
                        runnable = runQueue.poll()
                    }
                    selector.selectNow()
                }
                else {
                    val wakeTime = calculateSelectTimeout()

                    when {
                        wakeTime == null -> selector.select()
                        wakeTime < 2 -> selector.selectNow()
                        else -> selector.select(wakeTime - 1)
                    }
                }

                runTimedServices()

                val selected = selector.selectedKeys()
                selected.forEach { key ->
                    processKey(key)
                }
                selected.clear()

                if (hasInterWorkerMessages) {
                    var message = interWorkerMessages.poll()
                    while (message != null) {
                        if (!internalHandleMessage(message) && !handleMessage(message)) {
                            logger.warn { "Unhandled message : $message" }
                        }
                        message = interWorkerMessages.poll()
                    }
                }
            }
            catch (ex: InterruptedException) {
                // shutting down
                break
            }
            catch (ex: ClosedSelectorException) {
                // shutting down
                break
            }
            catch (ex: Exception) {
                ex.printStackTrace()
            }
            catch (ex: Error) {
                ex.printStackTrace()
                throw ex
            }
        }

        try {
            shutdownInternal()
        }
        catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }

    private fun internalHandleMessage(message: Any): Boolean {
        when (message) {
            is PromiseCompletionMessage<*> -> message.complete()
            is AddServiceMessage -> startService(message.service)
            else -> return false
        }
        return true
    }

    open protected fun handleMessage(message: Any): Boolean = false

    private fun processKey(key: SelectionKey) {
        val handler = key.attachment()
        if (handler is SelectionKeyHandler) {
            handler.handle(key)
        }
        else {
            throw IllegalStateException("Cannot process non handlers: $handler")
        }
    }

    fun registerSelectionKeyHandler(selectable: SelectableChannel,
                                    handler: SelectionKeyHandler,
                                    interestOps: Int = 0): SelectionKey = selectable.register(selector, interestOps, handler)
}
