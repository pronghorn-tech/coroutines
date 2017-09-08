package tech.pronghorn.coroutines.service

import java.time.Duration

/**
 * A service that calls process() on an interval.
 *
 * @param interval time desired between calls to process(), actual time may be greater
 */
abstract class IntervalService(interval: Duration) : Service() {
    private val durationAsMillis = interval.toMillis()
    private var lastRunTime = System.currentTimeMillis()
    var nextRunTime = lastRunTime + durationAsMillis
        private set

    override suspend fun run() {
        process()
        lastRunTime = System.currentTimeMillis()
        nextRunTime = lastRunTime + durationAsMillis
        yieldAsync()
    }

    abstract fun process()
}
