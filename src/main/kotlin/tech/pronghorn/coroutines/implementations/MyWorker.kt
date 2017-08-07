package tech.pronghorn.coroutines.implementations

import mu.KotlinLogging
import tech.pronghorn.coroutines.core.CoroutineWorker
import tech.pronghorn.coroutines.service.Service
import java.nio.channels.SelectionKey

class MyWorker : CoroutineWorker() {
    override val logger = KotlinLogging.logger {}

    override val services: List<Service> = emptyList()

    override fun processKey(key: SelectionKey) {

    }

}
