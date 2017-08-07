package tech.pronghorn.coroutines.core

open class WorkItem<WorkType>(val work: WorkType) {
    val enqueueTime = System.nanoTime()
}
