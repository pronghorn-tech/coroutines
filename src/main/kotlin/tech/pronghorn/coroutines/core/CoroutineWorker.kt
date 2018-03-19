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

import tech.pronghorn.coroutines.services.NO_NEXT_RUN_TIME
import tech.pronghorn.coroutines.services.TimedService
import tech.pronghorn.plugins.internalQueue.InternalQueuePlugin
import tech.pronghorn.plugins.logging.LoggingPlugin
import tech.pronghorn.plugins.mpscQueue.MpscQueuePlugin
import tech.pronghorn.util.ignoreExceptions
import java.nio.channels.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.experimental.*

private val workerIDs = AtomicLong(0)

/**
 * Runs process() for each SelectionKey triggered by its Selector
 * This happens on A dedicated thread that runs when start() is called
 */
@RestrictsSuspension
public open class CoroutineWorker : Lifecycle() {
    public val workerID = workerIDs.incrementAndGet()
    protected val logger = LoggingPlugin.get(javaClass)
    private val selector: Selector = Selector.open()
    private val workerThread = object : WorkerThread(this, "${javaClass.simpleName}-$workerID") {
        override fun run() = threadStart()
    }
    private val runningServices: MutableList<Service> = mutableListOf()
    private val timedServices: MutableList<TimedService> = mutableListOf()
    private val externalWork = MpscQueuePlugin.getUnbounded<() -> Unit>()
    private val coroutineRunQueue = InternalQueuePlugin.getUnbounded<Continuation<*>>()
    private val startedLock = ReentrantLock()
    private val startedCondition = startedLock.newCondition()
    @Volatile
    private var hasExternalWork = false
    @Volatile
    private var started = false
    private var selectorWoke = false
    private var hasRegisteredSelectionKeys = false

    protected open val initialServices: List<Service> = emptyList()

    internal fun getInitialServiceCount(): Int = initialServices.size

    public fun <T> launchWorkerCoroutine(block: suspend () -> T) {
        if (DEBUG && !isWorkerThread()) {
            throw IllegalStateException("launchWorkerCoroutine must be called from within the worker.")
        }
        block.startCoroutine(PronghornCoroutine(WorkerCoroutineContext(this)))
    }

    internal fun resumeContinuation(continuation: Continuation<*>) {
        coroutineRunQueue.add(continuation)
        if (!selectorWoke) {
            selectorWoke = true
            selector.wakeup()
        }
    }

    public fun isWorkerThread() = Thread.currentThread() == workerThread

    public fun addService(service: Service) {
        if (service.worker != this) {
            throw IllegalStateException("Service $service cannot be added to worker $workerID, it is bound to worker ${service.worker.workerID}")
        }

        executeInWorker {
            startService(service)
        }
    }

    private fun startService(service: Service) {
        if (service.worker != this) {
            throw IllegalStateException("Service $service cannot be added to worker $workerID, it is bound to worker ${service.worker.workerID}")
        }
        else if (runningServices.contains(service)) {
            throw IllegalStateException("Attempt to start service $service twice.")
        }
        else {
            if (service is TimedService) {
                timedServices.add(service)
            }
            service.start()
            runningServices.add(service)
        }
    }

    internal fun start() {
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

    internal fun shutdown() = lifecycleShutdown()

    public fun getRunningServices(): List<Service> = runningServices.toList()

    public inline fun <reified ServiceType : Service> getService(): ServiceType? {
        getRunningServices().forEach { service ->
            if (service is ServiceType) {
                return service
            }
        }
        return null
    }

    private fun threadStart() {
        startedLock.lock()
        try {
            lifecycleStart()
            logger.debug { "$workerID starting" }
            initialServices.forEach { service -> startService(service) }
        }
        finally {
            started = true
            startedCondition.signal()
            startedLock.unlock()
        }

        run()
    }

    final override fun onLifecycleStart() = Unit

    final override fun onLifecycleShutdown() {
        logger.debug { "$workerID Shutting down." }
        ignoreExceptions(
                { coroutineRunQueue.clear() },
                { runningServices.forEach(Service::shutdown) },
                { selector.close() },
                { workerThread.interrupt() },
                { workerThread.join() }
        )
    }

    private fun runTimedServices() {
        if (timedServices.isEmpty()) {
            return
        }

        val now = System.nanoTime()
        var x = 0
        while (x < timedServices.size) {
            val service = timedServices[x]
            val nextRunTime = service.getNextRunTime()
            if (nextRunTime != NO_NEXT_RUN_TIME && nextRunTime - now < 0) {
                service.wake()
            }
            x += 1
        }
    }

    private fun nanosUntilNextTimedServices(): Long {
        if (timedServices.isEmpty()) {
            return NO_NEXT_RUN_TIME
        }

        var selectTimeout = NO_NEXT_RUN_TIME
        var x = 0
        val now = System.nanoTime()
        while (x < timedServices.size) {
            val service = timedServices[x]
            x += 1
            val nextRunTime = service.getNextRunTime()
            if (nextRunTime == NO_NEXT_RUN_TIME) {
                continue
            }
            val timeUntilNextRun = nextRunTime - now
            if (timeUntilNextRun < 0) {
                return 0
            }

            if (selectTimeout == NO_NEXT_RUN_TIME || timeUntilNextRun < selectTimeout) {
                selectTimeout = timeUntilNextRun
            }
        }

        return selectTimeout
    }

    private fun run() {
        while (true) {
            try {
                var toResume = coroutineRunQueue.poll()
                val selectedCount = if (toResume != null) {
                    var run = 0
                    val cycleStartTime = System.nanoTime()
                    while (true) {
                        (toResume.context as PronghornCoroutineContext).resume(toResume)
                        run += 1
                        // TODO: these service limits should be configurable
                        if (run % 16 == 0 && System.nanoTime() - cycleStartTime > 100000) {
                            break
                        }
                        toResume = coroutineRunQueue.poll() ?: break
                    }
                    if (hasRegisteredSelectionKeys) {
                        selector.selectNow()
                    }
                    else {
                        0
                    }
                }
                else {
                    val wakeTime = nanosUntilNextTimedServices()
                    when {
                        wakeTime == NO_NEXT_RUN_TIME -> selector.select()
                        wakeTime < 1000000 -> selector.selectNow()
                        else -> {
                            // convert because selector.select() only has millisecond precision where service timeouts are calculated in nanoseconds
                            selector.select(wakeTime.div(1000000L) - 1)
                        }
                    }
                }
                selectorWoke = false

                runTimedServices()

                if (selectedCount > 0) {
                    val selected = selector.selectedKeys()
                    selected.forEach { key ->
                        processKey(key)
                    }
                    selected.clear()
                }

                if (hasExternalWork) {
                    hasExternalWork = false
                    var work = externalWork.poll()
                    while (work != null) {
                        // TODO: what happens if I throw an exception in an external work?
                        work()
                        work = externalWork.poll()
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

        if (!isShutdown()) {
            shutdown()
        }
    }

    public fun executeInWorker(block: () -> Unit) {
        if (isWorkerThread()) {
            block()
        }
        else {
            externalWork.add(block)
            hasExternalWork = true
            selector.wakeup()
        }
    }

    public fun executeSuspendingInWorker(block: suspend () -> Unit) {
        if (isWorkerThread()) {
            launchWorkerCoroutine(block)
        }
        else {
            externalWork.add { launchWorkerCoroutine(block) }
            hasExternalWork = true
            selector.wakeup()
        }
    }

//    public fun <T> executeInWorker(block: () -> T): CoroutineFuture<T> {
//        if (isWorkerThread()) {
//            return CoroutineFuture.completed(block())
//        }
//        else {
//            val future = CoroutineFuture<T>()
//            externalWork.add {
//                val promise = future.promise()
//                promise.complete(block())
//                hasExternalWork = true
//                selector.wakeup()
//            }
//            return future
//        }
//    }

    private fun processKey(key: SelectionKey) = (key.attachment() as SelectionKeyHandler).handle(key)

    public fun registerSelectionKeyHandler(selectable: SelectableChannel,
                                           handler: SelectionKeyHandler,
                                           interestOps: Int = 0): SelectionKey {
        if (DEBUG && !isWorkerThread()) {
            throw IllegalStateException("registerSelectionKeyHandler must be called from the worker thread")
        }
        hasRegisteredSelectionKeys = true
        return selectable.register(selector, interestOps, handler)
    }

//    public fun NEWregisterSelectionKeyHandler(selectable: SelectableChannel,
//                                              handler: SelectionKeyHandler,
//                                              interestOps: Int = 0): CoroutineFuture<SelectionKey> {
//        hasRegisteredSelectionKeys = true
//        if (isWorkerThread()) {
//            return CoroutineFuture.completed(selectable.register(selector, interestOps, handler))
//        }
//        else {
//            return executeInWorker<SelectionKey> {
//                selectable.register(selector, interestOps, handler)
//            }
//        }
//    }
}
