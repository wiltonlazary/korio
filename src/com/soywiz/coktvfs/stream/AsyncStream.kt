package com.soywiz.coktvfs.stream

import com.soywiz.coktvfs.*
import com.soywiz.coktvfs.async.asyncFun
import com.soywiz.coktvfs.async.executeInWorker
import com.soywiz.coktvfs.util.*
import java.nio.charset.Charset

open class AsyncStream {
    suspend open fun read(buffer: ByteArray, offset: Int, len: Int): Int = throw UnsupportedOperationException()
    suspend open fun write(buffer: ByteArray, offset: Int, len: Int): Unit = throw UnsupportedOperationException()
    suspend open fun setPosition(value: Long): Unit = throw UnsupportedOperationException()
    suspend open fun getPosition(): Long = throw UnsupportedOperationException()
    suspend open fun setLength(value: Long): Unit = throw UnsupportedOperationException()
    suspend open fun getLength(): Long = throw UnsupportedOperationException()
    internal val temp = ByteArray(16)
}

class SliceAsyncStream(val base: AsyncStream, val baseOffset: Long, val baseLength: Long) : AsyncStream() {
    var position = 0L

    suspend override fun read(buffer: ByteArray, offset: Int, len: Int): Int = asyncFun {
        val old = base.getPosition()
        base.setPosition(this.baseOffset + this.position)
        val res = base.read(buffer, offset, len)
        this.position += res
        base.setPosition(old)
        res
    }

    suspend override fun write(buffer: ByteArray, offset: Int, len: Int) = asyncFun {
        val old = base.getPosition()
        base.setPosition(this.baseOffset + this.position)
        base.write(buffer, offset, len)
        this.position += len
        base.setPosition(old)
    }

    suspend override fun setPosition(value: Long) {
        position = value
    }

    suspend override fun getPosition(): Long {
        return position
    }

    suspend override fun getLength(): Long {
        return baseLength
    }
}

suspend fun AsyncStream.slice(position: Long, length: Long): AsyncStream = asyncFun {
    // @TODO: Check bounds
    if (this is SliceAsyncStream) {
        SliceAsyncStream(this.base, this.baseOffset + position, length)
    } else {
        SliceAsyncStream(this, position, length)
    }
}

suspend fun AsyncStream.slice(): AsyncStream = asyncFun {
    this.slice(getPosition(), getAvailable())
}

suspend fun AsyncStream.readSlice(length: Long): AsyncStream = asyncFun {
    val start = getPosition()
    val out = this.slice(start, length)
    setPosition(start + length)
    out
}

suspend fun AsyncStream.getAvailable(): Long = asyncFun { getLength() - getPosition() }

suspend fun AsyncStream.readStringz(len: Int, charset: Charset = Charsets.UTF_8): String = asyncFun {
    val res = readBytes(len)
    val index = res.indexOf(0.toByte())
    String(res, 0, if (index < 0) len else index, charset)
}

suspend fun AsyncStream.readString(len: Int, charset: Charset = Charsets.UTF_8): String = asyncFun { readBytes(len).toString(charset) }
suspend fun AsyncStream.writeString(string: String, charset: Charset = Charsets.UTF_8): Unit = asyncFun { writeBytes(string.toByteArray(charset)) }

suspend fun AsyncStream.readExact(buffer: ByteArray, offset: Int, len: Int) = asyncFun {
    var remaining = len
    var coffset = offset
    //println("-- ($this:$len)")
    while (remaining > 0) {
        val read = read(buffer, coffset, remaining)
        //println("READ: $read")
        if (read < 0) break
        if (read == 0) throw IllegalStateException("Not enough data")
        coffset += read
        remaining -= read
    }
}

suspend fun AsyncStream.readTemp(len: Int): ByteArray = asyncFun { temp.apply { readExact(temp, 0, len) } }

suspend fun AsyncStream.readBytes(len: Int): ByteArray = asyncFun { ByteArray(len).apply { read(this, 0, len) } }
suspend fun AsyncStream.readBytesExact(len: Int): ByteArray = asyncFun { ByteArray(len).apply { readExact(this, 0, len) } }
suspend fun AsyncStream.writeBytes(data: ByteArray): Unit = write(data, 0, data.size)

suspend fun AsyncStream.readU8(): Int = asyncFun { readTemp(1).readU8(0) }

suspend fun AsyncStream.readU16_le(): Int = asyncFun { readTemp(2).readU16_le(0) }
suspend fun AsyncStream.readU32_le(): Long = asyncFun { readTemp(4).readU32_le(0) }

suspend fun AsyncStream.readS16_le(): Int = asyncFun { readTemp(2).readS16_le(0) }
suspend fun AsyncStream.readS32_le(): Int = asyncFun { readTemp(4).readS32_le(0) }
suspend fun AsyncStream.readS64_le(): Long = asyncFun { readTemp(8).readS64_le(0) }


suspend fun AsyncStream.readU16_be(): Int = asyncFun { readTemp(2).readU16_be(0) }
suspend fun AsyncStream.readU32_be(): Long = asyncFun { readTemp(4).readU32_be(0) }

suspend fun AsyncStream.readS16_be(): Int = asyncFun { readTemp(2).readS16_be(0) }
suspend fun AsyncStream.readS32_be(): Int = asyncFun { readTemp(4).readS32_be(0) }
suspend fun AsyncStream.readS64_be(): Long = asyncFun { readTemp(8).readS64_be(0) }

suspend fun AsyncStream.readAvailable(): ByteArray = asyncFun { readBytes(getAvailable().toInt()) }

fun SyncStream.toAsync() = object : AsyncStream() {
    val sync = this@toAsync
    suspend override fun read(buffer: ByteArray, offset: Int, len: Int): Int = executeInWorker { sync.read(buffer, offset, len) }
    suspend override fun write(buffer: ByteArray, offset: Int, len: Int) = executeInWorker { sync.write(buffer, offset, len) }
    suspend override fun setPosition(value: Long) = executeInWorker { sync.position = value }
    suspend override fun getPosition(): Long = executeInWorker { sync.position }
    suspend override fun setLength(value: Long)  = executeInWorker { sync.length = value }
    suspend override fun getLength(): Long  = executeInWorker { sync.length }
}