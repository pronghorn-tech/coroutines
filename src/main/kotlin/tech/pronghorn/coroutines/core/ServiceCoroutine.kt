package tech.pronghorn.coroutines.core

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

class ServiceCoroutine<T>(override val context: CoroutineContext) : Continuation<T> {
    override fun resume(value: T) {}

    override fun resumeWithException(exception: Throwable) {}
}
