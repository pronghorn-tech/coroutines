package tech.pronghorn.coroutines.services

import tech.pronghorn.coroutines.core.PronghornCoroutineContext
import tech.pronghorn.coroutines.core.suspendCoroutine
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

// allows custom control of timing via implementation of getNextRunTime()
abstract class VariableTimedService: TimedService() {
    private var continuation: Continuation<Unit>? = null

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun sleepAsync() {
        suspendCoroutine { continuation: Continuation<Unit> ->
            this.continuation = continuation
            COROUTINE_SUSPENDED
        }
    }

    final override fun wake(): Boolean {
        val continuation = this.continuation
        if(continuation != null){
            this.continuation = null
            (continuation.context as PronghornCoroutineContext).wake(continuation, Unit)
            return true
        }
        return false
    }

    final override suspend fun run() {
        while (isRunning()) {
            sleepAsync()
            process()
        }
    }

    protected abstract suspend fun process()
}
