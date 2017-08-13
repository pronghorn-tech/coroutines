package tech.pronghorn.coroutines.core

import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine

fun <T> myRun(context: CoroutineContext,
              block: suspend () -> T) {
    val coroutine = ServiceCoroutine<T>(context)
    block.startCoroutine(coroutine)
}
