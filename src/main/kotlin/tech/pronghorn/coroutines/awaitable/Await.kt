package tech.pronghorn.coroutines.awaitable

class AwaitResult2<A, B>(val a: A,
                         val b: B) {
    operator fun component1() = a
    operator fun component2() = b
}

class AwaitResult3<A, B, C>(val a: A,
                            val b: B,
                            val c: C) {
    operator fun component1() = a
    operator fun component2() = b
    operator fun component3() = c
}

class AwaitResult4<A, B, C, D>(val a: A,
                               val b: B,
                               val c: C,
                               val d: D) {
    operator fun component1() = a
    operator fun component2() = b
    operator fun component3() = c
    operator fun component4() = d
}

suspend fun <T> await(awaitable: Awaitable<T>): T {
    return awaitable.awaitAsync()
}

suspend fun <A, B> await(awaitableA: Awaitable<A>,
                         awaitableB: Awaitable<B>): AwaitResult2<A, B> {
    return AwaitResult2(
            awaitableA.awaitAsync(),
            awaitableB.awaitAsync()
    )
}

suspend fun <A, B, C> await(awaitableA: Awaitable<A>,
                            awaitableB: Awaitable<B>,
                            awaitableC: Awaitable<C>): AwaitResult3<A, B, C> {
    return AwaitResult3(
            awaitableA.awaitAsync(),
            awaitableB.awaitAsync(),
            awaitableC.awaitAsync()
    )
}

suspend fun <A, B, C, D> await(awaitableA: Awaitable<A>,
                               awaitableB: Awaitable<B>,
                               awaitableC: Awaitable<C>,
                               awaitableD: Awaitable<D>): AwaitResult4<A, B, C, D> {
    return AwaitResult4(
            awaitableA.awaitAsync(),
            awaitableB.awaitAsync(),
            awaitableC.awaitAsync(),
            awaitableD.awaitAsync()
    )
}
