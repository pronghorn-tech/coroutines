package tech.pronghorn.coroutines.core

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.createCoroutine
import kotlin.coroutines.experimental.startCoroutine

fun <T> myLaunch(context: CoroutineContext,
                 block: suspend () -> T): Continuation<Unit> {
    val coroutine = ServiceCoroutine<T>(context)
    return block.createCoroutine(coroutine)
}

fun <T> myRun(context: CoroutineContext,
              block: suspend () -> T) {
    val coroutine = ServiceCoroutine<T>(context)
    block.startCoroutine(coroutine)
}
