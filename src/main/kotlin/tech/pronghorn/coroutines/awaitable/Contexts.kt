package tech.pronghorn.coroutines.awaitable

import tech.pronghorn.coroutines.service.Service
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.CoroutineContext

class ServiceCoroutineContext(val service: Service) : AbstractCoroutineContextElement(ServiceCoroutineContext) {
    companion object Key : CoroutineContext.Key<ServiceCoroutineContext>
}

class ServiceManagedCoroutineContext(val service: Service) : AbstractCoroutineContextElement(ServiceManagedCoroutineContext) {
    companion object Key : CoroutineContext.Key<ServiceManagedCoroutineContext>
}
