import java.time.Duration

private val defaultEventually: Duration = Duration.ofSeconds(1)
private val interval: Duration = Duration.ofMillis(10)

fun eventually(duration: Duration = defaultEventually, f: () -> Unit): Unit {
    val end = System.nanoTime() + (duration.seconds * 1000000000L) + duration.nano
    val millis = (interval.nano / 1000000).toLong()
    var times = 0
    var lastException: Throwable? = null
    while (System.nanoTime() < end) {
        try {
            f()
            return
        }
        catch (ex: Exception) {
            lastException = ex
            // ignore and proceed
        }
        catch (ex: AssertionError) {
            lastException = ex
            // ignore and proceed
        }
        Thread.sleep(millis)

        times++
    }
    throw AssertionError("Test failed after ${duration.toMillis()} ms. Attempted $times times.\nLast exception: ${lastException?.message}")
}
