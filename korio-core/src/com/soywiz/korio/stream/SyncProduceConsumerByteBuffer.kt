package com.soywiz.korio.stream

import com.soywiz.korio.util.indexOf
import java.io.ByteArrayOutputStream
import java.util.*

class SyncProduceConsumerByteBuffer : SyncOutputStream, SyncInputStream {
	companion object {
		private val EMPTY = byteArrayOf()
	}

	private var current: ByteArray = EMPTY
	private var currentPos = 0
	private val buffers = LinkedList<ByteArray>()
	private var availableInBuffers = 0
	private val availableInCurrent: Int get() = current.size - currentPos

	val available: Int get() = availableInCurrent + availableInBuffers

	fun produce(data: ByteArray) {
		buffers += data
		availableInBuffers += data.size
	}

	private fun useNextBuffer() {
		current = if (buffers.isEmpty()) EMPTY else buffers.removeFirst()
		currentPos = 0
		availableInBuffers -= current.size
	}

	private fun ensureCurrentBuffer() {
		if (availableInCurrent <= 0) {
			useNextBuffer()
		}
	}

	fun consume(data: ByteArray, offset: Int = 0, len: Int = data.size): Int {
		var totalRead = 0
		var remaining = len
		var outputPos = offset
		while (remaining > 0) {
			ensureCurrentBuffer()
			val readInCurrent = Math.min(availableInCurrent, len)
			if (readInCurrent <= 0) break
			System.arraycopy(current, currentPos, data, outputPos, readInCurrent)
			currentPos += readInCurrent
			remaining -= readInCurrent
			totalRead += readInCurrent
			outputPos += readInCurrent
		}
		return totalRead
	}

	fun consume(len: Int): ByteArray = ByteArray(len).run { Arrays.copyOf(this, consume(this, 0, len)) }

	fun consumeUntil(end: Byte, including: Boolean = true): ByteArray {
		val out = ByteArrayOutputStream()
		while (true) {
			ensureCurrentBuffer()
			if (availableInCurrent <= 0) break // no more data!
			val p = current.indexOf(currentPos, end)
			val pp = if (p < 0) current.size else if (including) p + 1 else p
			val len = pp - currentPos
			if (len > 0) out.write(current, currentPos, len)
			currentPos += len
			if (p >= 0) break // found!
		}
		return out.toByteArray()
	}

	override fun write(buffer: ByteArray, offset: Int, len: Int) {
		produce(Arrays.copyOfRange(buffer, offset, offset + len))
	}

	override fun read(buffer: ByteArray, offset: Int, len: Int): Int {
		return consume(buffer, offset, len)
	}
}