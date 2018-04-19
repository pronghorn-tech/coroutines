/*
package tech.pronghorn.coroutines.awaitable.buffer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import tech.pronghorn.test.PronghornTest
import tech.pronghorn.test.lightRepeatCount

class InternalSingleConsumerPreallocatedQueueTests : PronghornTest() {
    class DataHolder(var longA: Long = 0,
                     var longB: Long = 0)

    @RepeatedTest(lightRepeatCount)
    fun stressTest() {
        val buffer = InternalSingleConsumerPreallocatedQueue(1024, { DataHolder() })
        val writer = buffer.writer
        val reader = buffer.reader

        val count = 100000000L
        val pre = System.currentTimeMillis()
        var x = 0L
        var total = 0L
        while (x < count) {
            while (x < count && writer.offer { it.longA = x; it.longB = x }){
                x += 1
            }
            writer.publish()

            var result = reader.poll()
            while(result != null){
                total += result.longA + result.longB
                result = reader.poll()
            }
            reader.commit()
        }

        val post = System.currentTimeMillis()

        var z= 0
        var expectedTotal = 0L
        while(z < count){
            expectedTotal += z * 2
            z += 1
        }

        println("$total : $expectedTotal")
        logger.info { "Took ${post - pre}ms for $count, ${(count / Math.max(1, (post - pre))) / 1000.0} million per second" }
        assertEquals(expectedTotal, total)
    }
}
*/
