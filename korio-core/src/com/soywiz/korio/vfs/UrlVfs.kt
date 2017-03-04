package com.soywiz.korio.vfs

import com.soywiz.korio.net.http.Http
import com.soywiz.korio.net.http.createHttpClient
import com.soywiz.korio.stream.AsyncStream
import com.soywiz.korio.stream.AsyncStreamBase
import com.soywiz.korio.stream.buffered
import com.soywiz.korio.stream.toAsyncStream
import java.io.FileNotFoundException
import java.net.URL

fun UrlVfs(url: String): VfsFile = UrlVfsImpl(url).root
fun UrlVfs(url: URL): VfsFile = UrlVfs(url.toString())

private class UrlVfsImpl(val url: String) : Vfs() {
	override val absolutePath: String = url
	val client = createHttpClient()

	private fun getFullUrl(path: String) = url.trim('/') + '/' + path.trim('/')

	//suspend override fun open(path: String, mode: VfsOpenMode): AsyncStream {
	//	return if (mode.write) {
	//		TODO()
	//	} else {
	//		client.request(HttpClient.Method.GET, getFullUrl(path)).content.toAsyncStream()
	//	}
	//}

	suspend override fun open(path: String, mode: VfsOpenMode): AsyncStream {
		val fullUrl = getFullUrl(path)
		val stat = stat(path)

		if (!stat.exists) throw FileNotFoundException("Unexistant $fullUrl")

		return object : AsyncStreamBase() {
			suspend override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int {
				if (len == 0) return 0
				val res = client.request(Http.Method.GET, fullUrl, Http.Headers(mapOf(
						"range" to "bytes=$position-${position + len - 1}"
				)))
				val out = res.content.read(buffer, offset, len)
				return out
			}

			suspend override fun getLength(): Long = stat.size
		}.toAsyncStream().buffered()
		//}.toAsyncStream()
	}

	class HttpHeaders(val headers: Http.Headers) : Attribute

	suspend override fun put(path: String, content: AsyncStream, attributes: List<Attribute>) {
		val headers = attributes.get<HttpHeaders>()
		val mimeType = attributes.get<MimeType>() ?: MimeType.APPLICATION_JSON
		val hheaders = headers?.headers ?: Http.Headers()
		val contentLength = content.getLength()

		client.request(Http.Method.PUT, getFullUrl(path), hheaders.withReplaceHeaders(
				"content-length" to "$contentLength",
				"content-type" to mimeType.mime
		), content)
	}

	suspend override fun stat(path: String): VfsStat {
		val result = client.request(Http.Method.HEAD, getFullUrl(path))

		return if (result.success) {
			createExistsStat(path, isDirectory = true, size = result.headers["content-length"]?.toLongOrNull() ?: 0L)
		} else {
			createNonExistsStat(path)
		}
	}
}
