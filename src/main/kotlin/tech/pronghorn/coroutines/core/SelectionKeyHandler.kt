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

import java.nio.channels.SelectionKey

sealed class SelectionKeyHandler<T> {
    protected abstract val attachment: T

    abstract fun handle(key: SelectionKey)
}

abstract class ReadSelectionKeyHandler<T>() : SelectionKeyHandler<T>() {
    abstract fun handleRead()

    override fun handle(key: SelectionKey) {
        if (key.isReadable) {
            handleRead()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

abstract class WriteSelectionKeyHandler<T>(override val attachment: T) : SelectionKeyHandler<T>() {
    abstract fun handleWrite()

    override fun handle(key: SelectionKey) {
        if (key.isWritable) {
            handleWrite()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

abstract class ReadWriteSelectionKeyHandler<T>(override val attachment: T) : SelectionKeyHandler<T>() {
    abstract fun handleRead()

    abstract fun handleWrite()

    override fun handle(key: SelectionKey) {
        var handled = false
        val isReadable = key.isReadable
        val isWritable = key.isWritable
        if (isReadable) {
            handled = true
            handleRead()
        }
        if (isWritable) {
            handled = true
            handleWrite()
        }
        if (!handled) {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

abstract class ReadWriteConnectSelectionKeyHandler<T>(override val attachment: T) : SelectionKeyHandler<T>() {
    abstract fun handleRead()

    abstract fun handleWrite()

    abstract fun handleConnect()

    override fun handle(key: SelectionKey) {
        var handled = false
        val isReadable = key.isReadable
        val isWritable = key.isWritable
        val isConnectable = key.isConnectable
        if (isReadable) {
            handled = true
            handleRead()
        }
        if (isWritable) {
            handled = true
            handleWrite()
        }
        if (isConnectable) {
            handled = true
            handleConnect()
        }
        if (!handled) {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

abstract class ConnectSelectionKeyHandler<T>(override val attachment: T) : SelectionKeyHandler<T>() {
    abstract fun handleConnect()

    override fun handle(key: SelectionKey) {
        if (key.isConnectable) {
            handleConnect()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

abstract class AcceptSelectionKeyHandler<T>(override val attachment: T) : SelectionKeyHandler<T>() {
    abstract fun handleAccept()

    override fun handle(key: SelectionKey) {
        if (key.isAcceptable) {
            handleAccept()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}
