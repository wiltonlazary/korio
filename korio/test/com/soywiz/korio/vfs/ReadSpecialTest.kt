package com.soywiz.korio.vfs

import com.soywiz.korio.async.sync
import com.soywiz.korio.async.syncTest
import org.junit.Assert
import org.junit.Test

class ReadSpecialTest {
	class CharArray2(val width: Int, val height: Int, val data: CharArray) {
		fun index(x: Int, y: Int): Int = y * width + x
		operator fun get(x: Int, y: Int): Char = data[index(x, y)]
		operator fun set(x: Int, y: Int, v: Char): Unit = run { data[index(x, y)] = v }
	}

	suspend fun VfsFile.readCharArray2(): CharArray2 {
		val text = this.readString()
		val side = Math.sqrt(text.length.toDouble()).toInt()
		return CharArray2(side, side, text.toCharArray())
	}

	@Test
	fun testReadSpecial2() = syncTest {
		val temp = MemoryVfs()
		val f2 = temp["korio.chararray2"]
		f2.writeString("123456789")
		val c2 = f2.readCharArray2()
		Assert.assertEquals('1', c2[0, 0])
		Assert.assertEquals('5', c2[1, 1])
		Assert.assertEquals('9', c2[2, 2])
	}
}