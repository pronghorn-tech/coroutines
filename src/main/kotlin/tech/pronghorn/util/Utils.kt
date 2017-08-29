package tech.pronghorn.util

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

fun SocketChannel.write(string: String) {
    val byteArray = string.toByteArray(StandardCharsets.UTF_8)
    if (byteArray.size > 4096) {
        throw Exception("SocketChannel.write(String) is strictly for short strings.")
    }
    val buffer = ByteBuffer.wrap(byteArray)
    assert(write(buffer) == byteArray.size)
}

fun runAllIgnoringExceptions(vararg blocks: () -> Unit): Unit {
    blocks.forEach { block ->
        try {
            block()
        }
        catch(ex: Exception) {
            // no-op
        }
    }
}

/**
 * A very simple Either implementation, largely inspired by https://github.com/MarioAriasC/funKTionale/issues/18
 * Represents a value of either the Left type or Right type, Left traditionally being the error case if applicable
 */
sealed class Either<out LeftType : Any?, out RightType : Any?>(protected open val l: LeftType?,
                                                               protected open val r: RightType?) {
    class Left<out LeftType : Any>(override val l: LeftType) : Either<LeftType, Nothing>(l, null) {
        inline fun <T1 : Any> run(block: ((LeftType) -> T1)): T1? {
            return block(value)
        }

        override fun toString() = "Left($l)"
        override fun equals(other: Any?): Boolean {
            return this === other || (other is Left<*> && l == other.l)
        }

        override fun hashCode() = l.hashCode()

        val value: LeftType
            get() = l
    }

    class Right<out RightType : Any>(override val r: RightType) : Either<Nothing, RightType>(null, r) {
        inline fun <T1 : Any> run(block: ((RightType) -> T1)): T1? {
            return block(value)
        }

        override fun toString() = "Right($r)"
        override fun equals(other: Any?): Boolean {
            return this === other || (other is Right<*> && r == other.r)
        }

        override fun hashCode() = r.hashCode()

        val value: RightType
            get() = r
    }

    fun isLeft() = l != null
    fun isRight() = r != null
}
