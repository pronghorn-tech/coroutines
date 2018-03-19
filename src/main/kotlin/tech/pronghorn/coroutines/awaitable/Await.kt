/*
 * Copyright 2017 Pronghorn Technology LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.pronghorn.coroutines.awaitable

public class AwaitResult2<A, B>(private val a: A,
                                private val b: B) {
    public operator fun component1() = a
    public operator fun component2() = b
}

public class AwaitResult3<A, B, C>(private val a: A,
                                   private val b: B,
                                   private val c: C) {
    public operator fun component1() = a
    public operator fun component2() = b
    public operator fun component3() = c
}

public class AwaitResult4<A, B, C, D>(private val a: A,
                                      private val b: B,
                                      private val c: C,
                                      private val d: D) {
    public operator fun component1() = a
    public operator fun component2() = b
    public operator fun component3() = c
    public operator fun component4() = d
}

@Suppress("NOTHING_TO_INLINE")
public suspend inline fun <T> await(awaitable: Awaitable<T>): T = awaitable.awaitAsync()

@Suppress("NOTHING_TO_INLINE")
public suspend inline fun <A, B> await(awaitableA: Awaitable<A>,
                                awaitableB: Awaitable<B>): AwaitResult2<A, B> {
    return AwaitResult2(
            awaitableA.awaitAsync(),
            awaitableB.awaitAsync()
    )
}

@Suppress("NOTHING_TO_INLINE")
public suspend inline fun <A, B, C> await(awaitableA: Awaitable<A>,
                                   awaitableB: Awaitable<B>,
                                   awaitableC: Awaitable<C>): AwaitResult3<A, B, C> {
    return AwaitResult3(
            awaitableA.awaitAsync(),
            awaitableB.awaitAsync(),
            awaitableC.awaitAsync()
    )
}

@Suppress("NOTHING_TO_INLINE")
public suspend inline fun <A, B, C, D> await(awaitableA: Awaitable<A>,
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
