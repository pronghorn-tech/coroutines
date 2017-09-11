package tech.pronghorn.coroutines.core

import tech.pronghorn.coroutines.awaitable.ExternalQueue
import tech.pronghorn.coroutines.awaitable.InternalFuture
import tech.pronghorn.coroutines.awaitable.InternalQueue
import tech.pronghorn.coroutines.awaitable.PromiseCompletionMessage
import tech.pronghorn.coroutines.service.*
import tech.pronghorn.plugins.logging.LoggingPlugin
import tech.pronghorn.plugins.mpscQueue.MpscQueuePlugin
import tech.pronghorn.util.runAllIgnoringExceptions
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.coroutines.experimental.RestrictsSuspension

private val schedulerID = AtomicLong(0)

/**
 * Runs process() for each SelectionKey triggered by its Selector
 * This happens on A dedicated thread that runs when start() is called
 */
@RestrictsSuspension
abstract class CoroutineWorker {
    protected val logger = LoggingPlugin.get(javaClass)
    protected val selector: Selector = Selector.open()
    val workerID = schedulerID.incrementAndGet()
    private val workerThread = thread(start = false, name = "${this::class.simpleName}-$workerID") {
        startInternal()
    }
    abstract val services: List<Service>
    private val intervalServices: List<IntervalService> by lazy(LazyThreadSafetyMode.NONE) {
        services.filterIsInstance<IntervalService>()
    }
    var isRunning = false
        private set
    private var nextTimedServiceTime: Long? = null
    private val runQueue = MpscQueuePlugin.get<Service>(1024)
    @Volatile private var hasInterWorkerMessages = false
    private val interWorkerMessages = MpscQueuePlugin.get<Any>(16384)
    private val startedLock = ReentrantLock()
    private val startedCondition = startedLock.newCondition()
    @Volatile private var started = false

    fun offerReady(service: Service) {
        if (!runQueue.offer(service)) {
            throw Exception("Unexpectedly failed to enqueue service.")
        }
    }

    fun isSchedulerThread() = Thread.currentThread() == workerThread

    fun sendInterWorkerMessage(message: Any): Boolean {
        if (interWorkerMessages.offer(message)) {
            hasInterWorkerMessages = true
            selector.wakeup()
            return true
        }
        else {
            logger.error { "Failed to send inter worker message, queue full." }
            return false
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> crossThreadCompletePromise(promise: InternalFuture.InternalPromise<T>,
                                       value: T) {
        sendInterWorkerMessage(PromiseCompletionMessage(promise, value))
    }

    inline fun <reified WorkType : Any, reified ServiceType : InternalQueueService<WorkType>>
            requestInternalWriter(): InternalQueue.InternalQueueWriter<WorkType> {
        val service = services.find { it is ServiceType }

        if (service != null) {
            return (service as ServiceType).getQueueWriter()
        }
        else {
            throw Exception("No service of requested type.")
        }
    }

    inline fun <reified WorkType : Any, reified ServiceType : SingleWriterExternalQueueService<WorkType>>
            requestSingleExternalWriter(): ExternalQueue.ExternalQueueWriter<WorkType> {
        val service = services.find { it is ServiceType }

        if (service != null) {
            return (service as ServiceType).getQueueWriter()
        }
        else {
            throw Exception("No service of requested type.")
        }
    }

    inline fun <reified WorkType : Any, reified ServiceType : MultiWriterExternalQueueService<WorkType>>
            requestMultiExternalWriter(): ExternalQueue.ExternalQueueWriter<WorkType> {
        val service = services.find { it is ServiceType }

        if (service != null) {
            return (service as ServiceType).getQueueWriter()
        }
        else {
            throw Exception("No service of requested type.")
        }
    }

    open protected fun onShutdown() = Unit

    open protected fun onStart() = Unit

    private fun startInternal() {
        startedLock.lock()
        try {
            onStart()
            services.forEach(Service::start)
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
        onShutdown()
        try {
            selector.close()
        }
        finally {
            isRunning = false
        }
    }

    fun shutdown() {
        logger.debug { "$workerID Requesting shutdown" }
        runAllIgnoringExceptions(
                { runQueue.clear() },
                { services.forEach(Service::shutdown) },
                { selector.close() },
                { workerThread.interrupt() },
                { workerThread.join() }
        )

    }

    fun runService(service: Service) {
        logger.debug { "$workerID Yielding to service: $service" }
        service.isQueued = false
        service.resume()
    }

    private fun runTimedServices() {
        if (nextTimedServiceTime == null) {
            return
        }

        val nextTime = nextTimedServiceTime
        val now = System.currentTimeMillis()
        if (nextTime != null && now > nextTime) {
            intervalServices.map { service ->
                if (now >= service.nextRunTime) {
                    runService(service)
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
                        wakeTime <= 0L -> selector.selectNow()
                        else -> selector.select(wakeTime + 1)
                    }
                    logger.debug { "$workerID Selector has woken up." }
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
                        if(!internalHandleMessage(message)) {
                            if(!handleMessage(message)){
                                logger.warn { "Unhandled message : $message" }
                            }
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

    open fun handleMessage(message: Any): Boolean = false

    private fun internalHandleMessage(message: Any): Boolean {
        if (message is PromiseCompletionMessage<*>) {
            message.complete()
            return true
        }

        return false
    }

    abstract fun processKey(key: SelectionKey)
}
