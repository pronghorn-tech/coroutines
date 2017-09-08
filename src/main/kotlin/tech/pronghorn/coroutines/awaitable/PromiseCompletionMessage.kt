package tech.pronghorn.coroutines.awaitable

data class PromiseCompletionMessage<T>(val promise: InternalFuture.InternalPromise<T>,
                                       val value: T) {
    fun complete() {
        promise.complete(value)
    }
}
