package tech.pronghorn.coroutines.oldtests

import tech.pronghorn.coroutines.awaitable.InternalFuture
import tech.pronghorn.coroutines.awaitable.await
import org.junit.Test
import tech.pronghorn.util.PronghornTest
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.test.assertEquals

class SimpleCoroutine(override val context: CoroutineContext) : Continuation<Unit> {
    override fun resume(value: Unit) {}

    override fun resumeWithException(exception: Throwable) {}
}

fun runCoroutine(block: suspend () -> Unit): Unit {
    var exception: Throwable? = null
    val wrappedBlock: suspend () -> Unit = {
        try {
            block()
        } catch (ex: Throwable) {
            exception = ex
        }
    }

    val coroutine = SimpleCoroutine(EmptyCoroutineContext)
    wrappedBlock.startCoroutine(coroutine)
    if (exception != null) {
        throw(exception!!)
    }
}

class FutureTests : PronghornTest() {
    /*
     * future should suspend for get() when incomplete {
     */
    @Test
    fun futureShouldSuspend() {
        val future = InternalFuture<Int>()
        val promise = future.promise()

        var total = 0
        runCoroutine {
            total += await(future)
        }

        assertEquals(0, total)
        promise.complete(1)
        assertEquals(1, total)
    }
}
