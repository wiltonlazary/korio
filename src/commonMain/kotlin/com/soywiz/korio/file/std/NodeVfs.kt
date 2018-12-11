@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package com.soywiz.korio.file.std

import com.soywiz.kds.*
import com.soywiz.korio.async.*
import com.soywiz.korio.file.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.stream.*

open class NodeVfs(val caseSensitive: Boolean = true) : Vfs() {
	val events = Signal<FileEvent>()

	open inner class Node(
		val name: String,
		val isDirectory: Boolean = false,
		parent: Node? = null
	) : Iterable<Node> {
		val nameLC = name.toLowerCase()
		override fun iterator(): Iterator<Node> = children.values.iterator()

		var parent: Node? = null
			set(value) {
				if (field != null) {
					field!!.children.remove(this.name)
					field!!.childrenLC.remove(this.nameLC)
				}
				field = value
				field?.children?.set(name, this)
				field?.childrenLC?.set(nameLC, this)
			}

		init {
			this.parent = parent
		}

		var data: Any? = null
		val children = lmapOf<String, Node>()
		val childrenLC = lmapOf<String, Node>()
		val root: Node get() = parent?.root ?: this
		var stream: AsyncStream? = null

		fun child(name: String): Node? = when (name) {
			"", "." -> this
			".." -> parent
			else -> if (caseSensitive) {
				children[name]
			} else {
				childrenLC[name.toLowerCase()]
			}
		}

		fun createChild(name: String, isDirectory: Boolean = false): Node =
			Node(name, isDirectory = isDirectory, parent = this)

		operator fun get(path: String): Node = access(path, createFolders = false)

		fun access(path: String, createFolders: Boolean = false): Node {
			var node = if (path.startsWith('/')) root else this
			for (part in VfsUtil.parts(path)) {
				var child = node.child(part)
				if (child == null && createFolders) child = node.createChild(part, isDirectory = true)
				node = child ?: throw com.soywiz.korio.FileNotFoundException("Can't find '$part' in $path")
			}
			return node
		}

		fun mkdir(name: String): Boolean {
			if (child(name) != null) {
				return false
			} else {
				createChild(name, isDirectory = true)
				return true
			}
		}
	}

	val rootNode = Node("", isDirectory = true)

	override suspend fun open(path: String, mode: VfsOpenMode): AsyncStream {
		val pathInfo = PathInfo(path)
		val folder = rootNode.access(pathInfo.folder)
		var node = folder.child(pathInfo.basename)
		val vfsFile = this@NodeVfs[path]
		if (node == null && mode.createIfNotExists) {
			node = folder.createChild(pathInfo.basename, isDirectory = false)
			val s = MemorySyncStream().base
			node.stream = object : AsyncStreamBase() {
				override suspend fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int {
					return s.read(position, buffer, offset, len)
				}

				override suspend fun write(position: Long, buffer: ByteArray, offset: Int, len: Int) {
					s.write(position, buffer, offset, len)
					events(
						FileEvent(
							FileEvent.Kind.MODIFIED,
							vfsFile
						)
					)
				}

				override suspend fun setLength(value: Long) {
					s.length = value
					events(
						FileEvent(
							FileEvent.Kind.MODIFIED,
							vfsFile
						)
					)
				}

				override suspend fun getLength(): Long = s.length
				override suspend fun close() = s.close()
			}.toAsyncStream()
		}
		return node?.stream?.duplicate() ?: throw com.soywiz.korio.FileNotFoundException(path)
	}

	override suspend fun stat(path: String): VfsStat {
		return try {
			val node = rootNode.access(path)
			//createExistsStat(path, isDirectory = node.isDirectory, size = node.stream?.getLength() ?: 0L) // @TODO: Kotlin wrong code generated!
			val length = node.stream?.getLength() ?: 0L
			createExistsStat(path, isDirectory = node.isDirectory, size = length)
		} catch (e: Throwable) {
			createNonExistsStat(path)
		}
	}

	override suspend fun list(path: String): SuspendingSequence<VfsFile> {
		return asyncGenerate {
			val node = rootNode[path]
			for ((name, _) in node.children) {
				yield(file("$path/$name"))
			}
		}
	}

	override suspend fun delete(path: String): Boolean {
		val node = rootNode[path]
		node.parent = null
		events(FileEvent(FileEvent.Kind.DELETED, this[path]))
		return true
	}

	override suspend fun mkdir(path: String, attributes: List<Attribute>): Boolean {
		val pathInfo = PathInfo(path)
		val out = rootNode.access(pathInfo.folder).mkdir(pathInfo.basename)
		events(FileEvent(FileEvent.Kind.CREATED, this[path]))
		return out
	}

	override suspend fun rename(src: String, dst: String): Boolean {
		if (src == dst) return false
		val dstInfo = PathInfo(dst)
		val srcNode = rootNode.access(src)
		val dstFolder = rootNode.access(dstInfo.folder)
		srcNode.parent = dstFolder
		events(
			FileEvent(
				FileEvent.Kind.RENAMED,
				this[src],
				this[dst]
			)
		)
		return true
	}

	override suspend fun watch(path: String, handler: (FileEvent) -> Unit): Closeable {
		return events { handler(it) }
	}

	override fun toString(): String = "NodeVfs"
}