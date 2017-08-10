package tech.pronghorn.coroutines.awaitable

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class CoroutineFuture<T> : Future<T> {
    @Volatile private var state = FutureState.INITIALIZED

    private var result: T? = null
    private var error: Throwable? = null
    private var callback: ((T) -> Unit)? = null

    constructor() {}

    private constructor(result: T) {
        this.result = result
        state = FutureState.COMPLETED_SUCCESS
    }

    fun promise(): CoroutinePromise<T> {
        if (state != FutureState.INITIALIZED) {
            throw IllegalStateException("The promise can only be fetched immediately after creation, and only once.")
        }
        state = FutureState.PROMISED
        return CoroutinePromise(this)
    }

    class CoroutinePromise<T>(private val future: CoroutineFuture<T>) {
        fun isCancelled(): Boolean = future.isCancelled

        fun complete(result: T) {
            if (future.state != FutureState.PROMISED) {
                throw RuntimeException("Attempted to complete future in invalid state : ${future.state}")
            }
            future.result = result
            future.state = FutureState.COMPLETED_SUCCESS
            synchronized(future) {
                future.callback?.invoke(result)
                (future as java.lang.Object).notifyAll()
            }
        }

        fun completeExceptionally(error: Throwable) {
            future.error = error
            future.state = FutureState.COMPLETED_EXCEPTION
            synchronized(future) {
                (future as java.lang.Object).notifyAll()
            }
        }
    }

    fun registerCallback(func: (T) -> Unit) {
        if (isDone) {
            func(result!!)
        } else {
            synchronized(this) {
                if (isDone) {
                    func(result!!)
                } else {
                    callback = func
                }
            }
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        return state == FutureState.CANCELLED
    }

    override fun isDone(): Boolean {
        return state != FutureState.PROMISED && state != FutureState.INITIALIZED
    }

    override fun get(): T {
        while (!isDone) {
            try {
                synchronized(this) {
                    if (!isDone) {
                        (this as java.lang.Object).wait()
                    }
                }
            } catch (ex: InterruptedException) {
                // TODO: something here
            }
        }

        if (state == FutureState.COMPLETED_SUCCESS) {
            return result!!
        } else {
            throw ExecutionException(error)
        }
    }

    override fun get(timeout: Long, unit: TimeUnit): T? {
        // TODO: implement this
        //        logger.error("Calling future.get with a timeout, NOT SUPPORTED YET!");
        return null
    }

    companion object {
        internal fun <T> success(result: T): CoroutineFuture<T> {
            return CoroutineFuture(result)
        }
    }
}


