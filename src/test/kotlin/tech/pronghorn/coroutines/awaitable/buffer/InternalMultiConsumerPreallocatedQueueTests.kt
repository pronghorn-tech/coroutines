/*
package tech.pronghorn.coroutines.awaitable.buffer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.test.*

class InternalMultiConsumerPreallocatedQueueTests : PronghornTest() {
    class DataHolder(var longA: Long = 0,
                     var longB: Long = 0)

    @RepeatedTest(lightRepeatCount)
    fun stressTest() {
        val buffer = InternalMulticastPreallocatedBuffer(1024, { DataHolder() })
        val writer = buffer.writer
        val reader = buffer.getReader()
//        val readerB = buffer.getReader()

        val count = 100000000L
        val pre = System.currentTimeMillis()
        var x = 0L
        var total = 0L
//        var totalB = 0L
        while (x < count) {
            while (x < count && writer.offer { it.longA = x; it.longB = x }) {
                x += 1
            }
            writer.publish()

            var result = reader.poll()
            while (result != null) {
                total += result.longA + result.longB
                result = reader.poll()
            }
            reader.commit()

//            result = readerB.poll()
//            while (result != null) {
//                totalB += result.longA + result.longB
//                result = readerB.poll()
//            }
//            readerB.commit()
        }

        val post = System.currentTimeMillis()

        var z = 0
        var expectedTotal = 0L
        while (z < count) {
            expectedTotal += z * 2
            z += 1
        }

        println("$total : $expectedTotal")
        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        assertEquals(expectedTotal, total)
//        assertEquals(expectedTotal, totalB)
    }
}
*/
