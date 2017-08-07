package tech.pronghorn.coroutines.core

data class TrackingResult(val queueServiceID: Long,
                          val processServiceID: Long,
                          val queueTime: Long,
                          val processTime: Long)
