package tech.pronghorn.coroutines.core

import org.junit.Test
import tech.pronghorn.test.CDBTest
import java.io.FileDescriptor
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod

const val SO_REUSEPORT = 15
class SocketTests : CDBTest() {
    fun setReusePort(serverChannel: ServerSocketChannel): Boolean {
        try {
            val fdProp = serverChannel::class.declaredMemberProperties.find { field -> field.name == "fd" } ?: return false
            fdProp.isAccessible = true
            val fd = fdProp.call(serverChannel)

            val netClass = this.javaClass.classLoader.loadClass("sun.nio.ch.Net").kotlin
            val setOpt = netClass.declaredFunctions.find { function ->
                function.name == "setIntOption0"
            } ?: return false
            setOpt.isAccessible = true
            setOpt.javaMethod?.invoke(null, fd, false, 1, SO_REUSEPORT, 1, false)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @Test
    fun socketTest() {
        val address = InetSocketAddress("192.168.10.106", 4321)

        val socketA: ServerSocketChannel = ServerSocketChannel.open()
        socketA.socket().reuseAddress = true
        setReusePort(socketA)
        val socketB: ServerSocketChannel = ServerSocketChannel.open()
        socketB.socket().reuseAddress = true
        setReusePort(socketB)

        Thread.sleep(1000)

        socketA.socket().bind(address, 64)
        socketB.socket().bind(address, 64)

        socketA.configureBlocking(false)
        socketB.configureBlocking(false)

        val selectorA = Selector.open()
        val selectorB = Selector.open()

        socketA.register(selectorA, SelectionKey.OP_ACCEPT)
        socketB.register(selectorB, SelectionKey.OP_ACCEPT)

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
                        println(socketB.accept())
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
