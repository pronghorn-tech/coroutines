package tech.pronghorn.coroutines.core

import tech.pronghorn.plugins.spsc.SpscQueuePlugin

abstract class Trackable {
    private val results = SpscQueuePlugin.get<TrackingResult>(256)
    private var totalQueueTime = 0L
    private var totalProcessTime = 0L

    fun appendResult(result: TrackingResult) {
        totalQueueTime += result.queueTime
        totalProcessTime += result.processTime

        if (!results.offer(result)) {
            results.clear()
            results.offer(result)
        }
    }
}
