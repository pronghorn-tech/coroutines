package tech.pronghorn.coroutines.core

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.EmptyCoroutineContext

class GenericCoroutine<T> : Continuation<T> {
    override val context = EmptyCoroutineContext

    override fun resume(value: T) {}

    override fun resumeWithException(exception: Throwable) {}
}
