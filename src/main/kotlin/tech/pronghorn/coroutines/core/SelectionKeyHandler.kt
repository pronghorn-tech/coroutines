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

private const val opsRead = SelectionKey.OP_READ
private const val opsWrite = SelectionKey.OP_WRITE
private const val opsReadWrite = SelectionKey.OP_READ.or(SelectionKey.OP_WRITE)
private const val opsReadWriteConnect = SelectionKey.OP_READ.or(SelectionKey.OP_WRITE).or(SelectionKey.OP_CONNECT)
private const val opsConnect = SelectionKey.OP_CONNECT
private const val opsAccept = SelectionKey.OP_ACCEPT

public interface SelectionKeyHandler {
    val handledOps: Int

    public fun handle(key: SelectionKey)
}

public interface ReadSelectionKeyHandler : SelectionKeyHandler {
    override val handledOps
        get() = opsRead

    public fun handleReadable()

    override fun handle(key: SelectionKey) {
        if (key.isReadable) {
            handleReadable()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

public interface WriteSelectionKeyHandler : SelectionKeyHandler {
    override val handledOps
        get() = opsWrite

    public fun handleWritable()

    override fun handle(key: SelectionKey) {
        if (key.isWritable) {
            handleWritable()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

public interface ReadWriteSelectionKeyHandler : SelectionKeyHandler {
    override val handledOps
        get() = opsReadWrite

    public fun handleReadable()

    public fun handleWritable()

    override fun handle(key: SelectionKey) {
        var handled = false
        val isReadable = key.isReadable
        val isWritable = key.isWritable
        if (isReadable) {
            handled = true
            handleReadable()
        }
        if (isWritable) {
            handled = true
            handleWritable()
        }
        if (!handled) {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

public interface ReadWriteConnectSelectionKeyHandler : SelectionKeyHandler {
    override val handledOps
        get() = opsReadWriteConnect

    public fun handleReadable()
    public fun handleWritable()
    public fun handleConnectable()

    override fun handle(key: SelectionKey) {
        var handled = false
        val isReadable = key.isReadable
        val isWritable = key.isWritable
        val isConnectable = key.isConnectable
        if (isReadable) {
            handled = true
            handleReadable()
        }
        if (isWritable) {
            handled = true
            handleWritable()
        }
        if (isConnectable) {
            handled = true
            handleConnectable()
        }
        if (!handled) {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

public interface ConnectSelectionKeyHandler : SelectionKeyHandler {
    override val handledOps
        get() = opsConnect

    public fun handleConnectable()

    override fun handle(key: SelectionKey) {
        if (key.isConnectable) {
            handleConnectable()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}

public interface AcceptSelectionKeyHandler : SelectionKeyHandler {
    override val handledOps
        get() = opsAccept

    public fun handleAcceptable()

    override fun handle(key: SelectionKey) {
        if (key.isAcceptable) {
            handleAcceptable()
        }
        else {
            throw IllegalStateException("Unhandled interest ops registered.")
        }
    }
}
