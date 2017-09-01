package tech.pronghorn.coroutines

import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.service.Service
import java.nio.channels.SelectionKey

class DummyWorker : CoroutineWorker() {
    override fun processKey(key: SelectionKey) {}
    override val services: List<Service> = listOf()
}
