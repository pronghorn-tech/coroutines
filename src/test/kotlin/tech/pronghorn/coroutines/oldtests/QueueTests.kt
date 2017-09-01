package tech.pronghorn.coroutines.oldtests

import org.junit.Test
import tech.pronghorn.coroutines.awaitable.InternalQueue
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.util.PronghornTest
import kotlin.test.assertEquals

class QueueTests : PronghornTest() {
    /*
     * queue should suspend for next() when empty
     */
    @Test
    fun queueShouldSuspendWhenEmpty() {
        val capacity = 1024
        val queue = InternalQueue<Int>(capacity)

        var total = 0

        runCoroutine {
            total += await(queue.queueReader)
        }

        assertEquals(0, total)

        runCoroutine {
            queue.queueWriter.addAsync(1)
        }

        assertEquals(1, total)
    }


    /*
     * queue should suspend for addAsync() when full
     */
    @Test
    fun queueShouldSuspendWhenFull() {
        val capacity = 4
        val queue = InternalQueue<Int>(capacity)

        var added = 0
        runCoroutine {
            while (true) {
                queue.queueWriter.addAsync(1)
                added += 1
            }
        }

        assertEquals(capacity, added)

        runCoroutine {
            await(queue.queueReader)
        }

        assertEquals(capacity + 1, added)
    }
}
