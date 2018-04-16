package tech.pronghorn.coroutines.core

sealed class ServiceLaunchStrategy<W: CoroutineWorker> {
    abstract class InstancePerWorker<W : CoroutineWorker> : ServiceLaunchStrategy<W>() {
        abstract fun launchService(worker: W): Service
    }

    abstract class SingleInstance<W : CoroutineWorker> : ServiceLaunchStrategy<W>() {
        abstract fun launchService(worker: W): Service
    }

    abstract class SingleInstancesColocated<W : CoroutineWorker>(val count: Int) : ServiceLaunchStrategy<W>() {
        abstract fun launchServices(worker: W): Set<Service>
    }
}
