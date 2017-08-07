package tech.pronghorn.coroutines.oldtests

import org.junit.Test
import tech.pronghorn.coroutines.awaitable.InternalFuture
import tech.pronghorn.coroutines.awaitable.InternalQueue
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.coroutines.core.myLaunch
import tech.pronghorn.coroutines.core.myRun
import tech.pronghorn.test.CDBTest
import tech.pronghorn.util.roundToPowerOfTwo
import kotlin.concurrent.thread
import kotlin.coroutines.experimental.*
import kotlin.test.assertEquals

object TestScheduler {
    private var a: Continuation<Unit>? = null
    private var b: Continuation<Unit>? = null
    private var x = 0
    private var next: Continuation<Unit>? = null

    fun setA(a: Continuation<Unit>) {
        TestScheduler.a = a
    }

    fun setB(b: Continuation<Unit>) {
        TestScheduler.b = b
        next = b
    }

    suspend fun yield() {
        suspendCoroutine<Unit> { coroutine ->
            val currNext = next
            next = coroutine
            currNext!!.resume(Unit)
        }
    }
}

class Testo : CDBTest() {
    val futureTestCount = 0
    val queueTestCount = 0
    val serviceTestCount = 64

    /*
     * coroutines should continuation
     */
    @Test
    fun coroutinesContinuation() {
        repeat(0) {
            var x = 0
            val pre = System.currentTimeMillis()

            var bs = 0
            val b = myLaunch(EmptyCoroutineContext) {
                while (true) {
                    bs += 1
                    TestScheduler.yield()
                }
            }

            TestScheduler.setB(b)

            while (x < 100000000) {
                myRun(EmptyCoroutineContext) {
                    x += 1
                    if (x % 100 == 0) {
                        TestScheduler.yield()
                    }
                }
            }

            assertEquals(100000000, x)

            val post = System.currentTimeMillis()
            println("${post - pre} : $x $bs")
        }
    }

    /*
     * futures should be awaitable
     */
    @Test
    fun futuresAwaitable() {
        repeat(futureTestCount) {
            var x = 0
            val future = InternalFuture<Int>()
            val promise = future.promise()

            myRun(EmptyCoroutineContext) {
                val value = await(future)
                x += value
            }

            assertEquals(0, x)
            promise.complete(1)
            assertEquals(1, x)
        }
    }

    /*
     * futures should be capable of being completed prior to a waiter being set
     */
    @Test
    fun futuresCompletedPrior() {
        repeat(futureTestCount) {
            var x = 0
            val future = InternalFuture<Int>()
            val promise = future.promise()
            promise.complete(1)

            myRun(EmptyCoroutineContext) {
                val value = await(future)
                x += value
            }

            assertEquals(1, x)
        }
    }

    /*
     * futures should support multiple waiters
     */
    @Test
    fun futuresMultipleWaiters() {
        repeat(futureTestCount) {
            var x = 0
            val future = InternalFuture<Int>()
            val promise = future.promise()

            myRun(EmptyCoroutineContext) {
                val value = await(future)
                x += value
            }

            myRun(EmptyCoroutineContext) {
                val value = await(future)
                x += value
            }

            assertEquals(0, x)
            promise.complete(1)
            assertEquals(2, x)
        }
    }

    /*
     * futures should should support having a continuation wait on multiple simultaneously
     */
    @Test
    fun futuresMultiWait(){
//        val stats = StatTracker()
        repeat(futureTestCount) {
            //                repeat(1024 * 64) {
            val pre = System.nanoTime()
            var x = 0
            val futureA = InternalFuture<Int>()
            val futureB = InternalFuture<Int>()
            val futureC = InternalFuture<Int>()
            val promiseA = futureA.promise()
            val promiseB = futureB.promise()
            val promiseC = futureC.promise()

            myRun(EmptyCoroutineContext) {
                val (a, b, c) = await(futureA, futureB, futureC)
                x += a
                x += b
                x += c
            }

            assertEquals(0, x)
            promiseA.complete(1)
            promiseB.complete(2)
            promiseC.complete(3)
            assertEquals(6, x)
            val post = System.nanoTime()
//            stats.addValue(post - pre)
//                }
//                println("Avg: ${Math.round(stats.mean)}, 99th: ${Math.round(stats.getPercentile(99.0))}, max: ${Math.round(stats.max)}")
//                stats.clear()
        }
    }

    /*
     * queues should suspend if adding to full
     */
    @Test
    fun queuesSuspendIfFull(){
        repeat(queueTestCount) {
            val queue = InternalQueue<Int>(roundToPowerOfTwo(4 + random.nextInt(128)))
            val toRemove = 4 + random.nextInt(128)

            var added = 0
            myRun(EmptyCoroutineContext) {
                while (true) {
                    queue.queueWriter.addAsync(added)
                    added += 1
                }
            }

            assertEquals(queue.capacity, added)

            var removed = 0
            myRun(EmptyCoroutineContext) {
                while (removed < toRemove) {
                    queue.queueReader.nextAsync()
                    removed += 1
                }
            }

            assertEquals(toRemove, removed)
            assertEquals(queue.capacity + toRemove, added)
        }
    }

    /*
     * queues should suspend if polling from empty
     */

    @Test
    fun queuesSuspendIfEmpty(){
        repeat(queueTestCount) {
            val queue = InternalQueue<Int>(roundToPowerOfTwo(4 + random.nextInt(128)))
            val toAdd = 4 + random.nextInt(128)

            var removed = 0
            myRun(EmptyCoroutineContext) {
                while (true) {
                    queue.queueReader.nextAsync()
                    removed += 1
                }
            }

            assertEquals(0, removed)

            var added = 0
            myRun(EmptyCoroutineContext) {
                while (added < toAdd) {
                    queue.queueWriter.addAsync(added)
                    added += 1
                }
            }

            assertEquals(toAdd, added)
            assertEquals(toAdd, removed)
        }
    }

    /*
     * services should service
     */

    @Test
    fun services(){
        repeat(serviceTestCount) {
            val threads = mutableListOf<Thread>()
            var t = 0
            val workCount = 10000000
            while (t < 8) {
                val thread = thread(start = false) {
                    val service = IntService()
                    val pre = System.currentTimeMillis()
                    myRun(TestServiceCoroutineContext(service)) {
                        service.run()
                    }

                    assertEquals(0, service.processCount)

                    var x = 0
                    myRun(PotatoCoroutineContext()) {
                        while (x < workCount) {
//                                service.get.addAsync(x)
                            x += 1
                        }
                        TODO()
//                            CoroutineScheduler.yield()
                    }

                    val post = System.currentTimeMillis()
                    assertEquals(x, service.processCount)
                    println("$x ${post - pre} : ${Math.round((workCount * (1000.0 / (post - pre))) / 1000000)} million per second -- ${service.processCount}")
                }
                threads.add(thread)
                t += 1
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }
    }
}

class PotatoCoroutineContext : AbstractCoroutineContextElement(PotatoCoroutineContext) {
    companion object Key : CoroutineContext.Key<PotatoCoroutineContext>
}

class TestServiceCoroutineContext(val service: TestService<*>) : AbstractCoroutineContextElement(TestServiceCoroutineContext) {
    companion object Key : CoroutineContext.Key<TestServiceCoroutineContext>
}

class IntService : TestService<Int>() {
    var processCount = 0

    override fun process(value: Int) {
        processCount += 1
    }
}

abstract class TestService<T> {
    val queue = InternalQueue<T>(1024)
    var continuation: Continuation<Any>? = null
    var wakeValue: Any? = null

    fun resume() {
        val continuation = this.continuation
        val wakeValue = this.wakeValue
        if (continuation != null) {
            when (wakeValue) {
                is Throwable -> continuation.resumeWithException(wakeValue)
                is Any -> continuation.resume(wakeValue)
                else -> continuation.resume(Unit)
            }
        }
    }

    fun <T> wake(value: T) {
        wakeValue = value

        if (continuation != null) {
//            CoroutineScheduler.offerReady(/*this*/TODO())
        } else {
            println("Unexpected wake")
            System.exit(1)
        }
    }

    abstract fun process(value: T)

    suspend fun run() {
        while (true) {
//            process(queue.queueReader.nextAsync())
        }
    }
}
