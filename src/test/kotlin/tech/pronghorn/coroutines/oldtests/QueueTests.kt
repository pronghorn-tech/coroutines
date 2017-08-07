package tech.pronghorn.coroutines.oldtests

import org.junit.Test
import tech.pronghorn.coroutines.awaitable.InternalQueue
import tech.pronghorn.test.CDBTest
import kotlin.test.assertEquals

class QueueTests : CDBTest() {
    /*
     * queue should suspend for next() when empty
     */
    @Test
    fun queueShouldSuspendWhenEmpty() {
        val capacity = 1024
        val queue = InternalQueue<Int>(capacity)

        var total = 0

        runCoroutine {
            total += queue.queueReader.nextAsync()
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
            queue.queueReader.nextAsync()
        }

        assertEquals(capacity + 1, added)
    }
}
