/*
 * Copyright 2017 Pronghorn Technology LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.pronghorn.coroutines.core

import tech.pronghorn.plugins.logging.LoggingPlugin

abstract class CoroutineApplication<W: CoroutineWorker> : Lifecycle() {
    protected val logger = LoggingPlugin.get(javaClass)
    protected abstract val workers: Set<W>

    public fun addService(generator: (W) -> Service) {
        workers.forEach { worker ->
            worker.addService(generator(worker))
        }
    }

    protected open val serviceLaunchStrategies: Set<ServiceLaunchStrategy<W>> = emptySet()

    public fun start() = lifecycleStart()

    public fun shutdown() = lifecycleShutdown()

    private fun launchServices() {
        if(serviceLaunchStrategies.isNotEmpty()){
            // create a map of worker to service count
            val workerServiceCounts = workers.map { worker ->
                worker to worker.getInitialServiceCount()
            }.toMap().toMutableMap()

            // start by handling the InstancePerWorker services
            serviceLaunchStrategies.filterIsInstance<ServiceLaunchStrategy.InstancePerWorker<W>>().forEach { strategy ->
                workers.forEach { worker ->
                    val service = strategy.launchService(worker)
                    logger.debug { "Launched service $service on worker ${worker.workerID}" }
                    worker.addService(service)
                    workerServiceCounts.compute(worker, { _, value -> value!! + 1 })
                }
            }

            // add colocated services together to the worker with the fewest services
            val colocatedStrategies = serviceLaunchStrategies.filterIsInstance<ServiceLaunchStrategy.SingleInstancesColocated<W>>().toMutableSet()
            while(colocatedStrategies.isNotEmpty()){
                val largestColocatedGroup = colocatedStrategies.maxBy { it.count }!!
                val workerWithFewestServices = workerServiceCounts.minBy { it.value }!!.key
                val colocatedServices = largestColocatedGroup.launchServices(workerWithFewestServices)
                if(colocatedServices.size != largestColocatedGroup.count){
                    throw IllegalStateException("Expected $largestColocatedGroup to launch ${largestColocatedGroup.count} services, it launched ${colocatedServices.size}")
                }
                colocatedServices.forEach { service ->
                    logger.debug { "Launched service $service on worker ${workerWithFewestServices.workerID}" }
                }
                colocatedServices.forEach(workerWithFewestServices::addService)
                workerServiceCounts.compute(workerWithFewestServices, { _, value -> value!! + largestColocatedGroup.count })
                colocatedStrategies.remove(largestColocatedGroup)
            }

            // add single instance services
            val singleInstanceAssignments = serviceLaunchStrategies.filterIsInstance<ServiceLaunchStrategy.SingleInstance<W>>().toMutableSet()
            singleInstanceAssignments.forEach { strategy ->
                val workerWithFewestServices = workerServiceCounts.minBy { it.value }!!.key
                val service = strategy.launchService(workerWithFewestServices)
                logger.debug { "Launched service $service on worker ${workerWithFewestServices.workerID}" }
                workerWithFewestServices.addService(service)
                workerServiceCounts.compute(workerWithFewestServices, { _, value -> value!! + 1 })
            }
        }
    }

    final override fun onLifecycleStart() {
        launchServices()
        workers.forEach(CoroutineWorker::start)
    }

    final override fun onLifecycleShutdown() {
        workers.forEach { worker ->
            try {
                if (worker.isRunning()) {
                    worker.shutdown()
                }
            }
            catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
