package tech.pronghorn.coroutines.core

import tech.pronghorn.coroutines.awaitable.ServiceCoroutineContext
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine

fun <T> launchServiceCoroutine(context: CoroutineContext,
                               block: suspend () -> T) {
    if(context is ServiceCoroutineContext) {
        val coroutine = ServiceCoroutine<T>(context)
        block.startCoroutine(coroutine)
    }
    else {
        throw Exception("Illegal coroutine context, service coroutines must be launched within a service context.")
    }
}
