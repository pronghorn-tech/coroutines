package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.InternalFuture
import tech.pronghorn.coroutines.awaitable.await

abstract class InternalSleepableService : Service() {
    private var sleepPromise: InternalFuture.InternalPromise<Unit>? = null

    suspend fun sleepAsync() {
        val future = InternalFuture<Unit>()
        sleepPromise = future.promise()
        await(future)
    }

    fun wake() {
        sleepPromise?.complete(Unit)
        sleepPromise = null
    }
}
