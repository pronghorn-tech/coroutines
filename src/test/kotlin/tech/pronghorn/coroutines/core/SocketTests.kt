package tech.pronghorn.coroutines.core

import org.junit.Test
import tech.pronghorn.test.CDBTest
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class SocketTests : CDBTest() {
    @Test
    fun socketTest() {
        val address = InetSocketAddress("192.168.10.106", 4321)

        val socketA: ServerSocketChannel = ServerSocketChannel.open()

        socketA.socket().bind(address, 64)

        socketA.configureBlocking(false)

        val selectorA = Selector.open()
        val selectorB = Selector.open()

        socketA.register(selectorA, SelectionKey.OP_ACCEPT)
        socketA.register(selectorB, SelectionKey.OP_ACCEPT)

        val lock = ReentrantLock()

        val threadA = thread(start = false) {
            while(true) {
                selectorA.select()
                val selected = selectorA.selectedKeys()
                selected.forEach { key ->
                    if(lock.tryLock()) {
                        println("threadA")
                        println(socketA.accept())
                        lock.unlock()
                    }

                }
                selected.clear()
            }
        }

        val threadB = thread(start = false) {
            while(true) {
                selectorB.select()
                val selected = selectorB.selectedKeys()
                selected.forEach { key ->
                    if(lock.tryLock()) {
                        println("threadB")
                        println(socketA.accept())
                        lock.unlock()
                    }
                }
                selected.clear()
            }
        }

        threadA.start()
        threadB.start()

        Thread.sleep(100)

        SocketChannel.open().connect(address)

        Thread.sleep(100)

        SocketChannel.open().connect(address)

        Thread.sleep(100)

        SocketChannel.open().connect(address)

        Thread.sleep(100)

        SocketChannel.open().connect(address)

        Thread.sleep(100)

        socketA.close()

        Thread.sleep(1000)
    }
}
