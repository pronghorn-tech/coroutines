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

interface SelectionKeyHandler {
    fun handle(key: SelectionKey)
}

interface ReadSelectionKeyHandler : SelectionKeyHandler {
    fun handleRead()

    override fun handle(key: SelectionKey) {
        if (key.isReadable) {
            handleRead()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

interface WriteSelectionKeyHandler : SelectionKeyHandler {
    fun handleWrite()

    override fun handle(key: SelectionKey) {
        if (key.isWritable) {
            handleWrite()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

interface ReadWriteSelectionKeyHandler : SelectionKeyHandler {
    fun handleRead()

    fun handleWrite()

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

interface ReadWriteConnectSelectionKeyHandler : SelectionKeyHandler {
    fun handleRead()

    fun handleWrite()

    fun handleConnect()

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

interface ConnectSelectionKeyHandler : SelectionKeyHandler {
    fun handleConnect()

    override fun handle(key: SelectionKey) {
        if (key.isConnectable) {
            handleConnect()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

interface AcceptSelectionKeyHandler : SelectionKeyHandler {
    fun handleAccept()

    override fun handle(key: SelectionKey) {
        if (key.isAcceptable) {
            handleAccept()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}
