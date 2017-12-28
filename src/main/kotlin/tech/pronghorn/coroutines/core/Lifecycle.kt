/*
 * Copyright 2018 Pronghorn Technology LLC
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

import tech.pronghorn.plugins.mpscQueue.MpscQueuePlugin
import tech.pronghorn.util.ignoreException
import tech.pronghorn.util.ignoreExceptions

public abstract class Lifecycle : Attachable() {
    private var state = LifecycleState.Initialized
    private val shutdownMethods = MpscQueuePlugin.getUnbounded<() -> Unit>()

    public fun isInitialized(): Boolean = state == LifecycleState.Initialized

    public fun isRunning(): Boolean = state == LifecycleState.Running

    public fun isShutdown(): Boolean = state == LifecycleState.Shutdown

    protected open fun onStart() = Unit

    protected open fun onShutdown() = Unit

    public fun registerShutdownMethod(method: () -> Unit) {
        shutdownMethods.add(method)
    }

    internal fun lifecycleStart() {
        if (isRunning()) {
            throw IllegalStateException("${this.javaClass.simpleName} cannot start, it is already running.")
        }
        if (isShutdown()) {
            throw IllegalStateException("${this.javaClass.simpleName} cannot start, it is already shut down.")
        }
        onStart()
        state = LifecycleState.Running
        onLifecycleStart()
    }

    internal fun lifecycleShutdown() {
        if (isInitialized()) {
            throw IllegalStateException("${this.javaClass.simpleName} cannot shutdown, it is not yet running.")
        }
        if (isShutdown()) {
            throw IllegalStateException("${this.javaClass.simpleName} cannot shutdown, it is already shut down.")
        }

        var method = shutdownMethods.poll()
        while(method != null){
            ignoreException(method)
            method = shutdownMethods.poll()
        }

        ignoreExceptions(
                { onShutdown() },
                { state = LifecycleState.Shutdown },
                { onLifecycleShutdown() }
        )
    }

    internal open fun onLifecycleStart() = Unit

    internal open fun onLifecycleShutdown() = Unit
}
