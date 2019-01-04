package com.soywiz.korio

import com.soywiz.korio.async.*
import com.soywiz.korio.file.*
import com.soywiz.korio.file.std.*
import com.soywiz.korio.net.*
import com.soywiz.korio.net.http.*
import com.soywiz.korio.net.ws.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.*
import kotlinx.coroutines.*
import org.khronos.webgl.*
import org.khronos.webgl.set
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.performance.*
import org.w3c.xhr.*
import kotlin.browser.*
import kotlin.collections.set
import kotlin.coroutines.*

val jsbaseUrl by lazy {
	val href = document.location?.href ?: "."
	if (href.endsWith("/")) href else href.substringBeforeLast('/')

}

abstract external class GlobalScope : EventTarget, WindowOrWorkerGlobalScope, GlobalPerformance {
	fun postMessage(message: dynamic, targetOrigin: dynamic = definedExternally, transfer: dynamic = definedExternally)
	fun requestAnimationFrame(callback: (Double) -> Unit): Int
	fun cancelAnimationFrame(handle: Int): Unit
}

val globalDynamic: dynamic = js("(typeof global !== 'undefined') ? global : self")
val global: GlobalScope  = globalDynamic
external val process: dynamic // node.js
external val navigator: dynamic // browser

//val isNodeJs by lazy { jsTypeOf(window) === "undefined" }

val isNodeJs by lazy { js("(typeof process === 'object' && typeof require === 'function')").unsafeCast<Boolean>() }
val isWeb by lazy { js("(typeof window === 'object')").unsafeCast<Boolean>() }
val isWorker by lazy { js("(typeof importScripts === 'function')").unsafeCast<Boolean>() }
val isShell get() = !isWeb && !isNodeJs && !isWorker

fun HTMLCollection.toList(): List<Element?> = (0 until length).map { this[it] }
fun <T : Element> HTMLCollection.toTypedList(): List<T> = (0 until length).map { this[it].unsafeCast<T>() }

private external class Date(time: Double)

fun jsNew(clazz: dynamic): dynamic = js("(new (clazz))()")
fun jsNew(clazz: dynamic, a0: dynamic): dynamic = js("(new (clazz))(a0)")
fun jsNew(clazz: dynamic, a0: dynamic, a1: dynamic): dynamic = js("(new (clazz))(a0, a1)")
fun jsNew(clazz: dynamic, a0: dynamic, a1: dynamic, a2: dynamic): dynamic = js("(new (clazz))(a0, a1, a2)")
fun jsEnsureNumber(v: dynamic): Number = js("+v")
fun jsEnsureInt(v: dynamic): Int = js("v|0")
fun jsEmptyObj(): dynamic = js("({})")
fun jsEmptyArray(): dynamic = js("([])")
fun jsObjectKeys(obj: dynamic): dynamic = js("Object.keys(obj)")
fun jsToArray(obj: dynamic): Array<Any?> = Array<Any?>(obj.length) { obj[it] }
fun jsArray(vararg elements: dynamic): Array<dynamic> {
	val out = jsEmptyArray()
	for (e in elements) out.push(e)
	return out
}

inline fun <reified T> jsToArrayT(obj: dynamic): Array<T> = Array<T>(obj.length) { obj[it] }
fun jsObject(vararg pairs: Pair<String, Any?>): dynamic {
	val out = jsEmptyObj()
	for (pair in pairs) out[pair.first] = pair.second
	return out
}

fun jsToObjectMap(obj: dynamic): Map<String, Any?>? {
	if (obj == null) return null
	val out = linkedMapOf<String, Any?>()
	val keys = jsObjectKeys(obj)
	for (n in 0 until keys.length) {
		val key = keys[n]
		out["$key"] = obj[key]
	}
	return out
}


class HttpClientBrowserJs : HttpClient() {
	override suspend fun requestInternal(
		method: Http.Method,
		url: String,
		headers: Http.Headers,
		content: AsyncStream?
	): Response {
		val deferred = CompletableDeferred<Response>(Job())
		val xhr = XMLHttpRequest()
		xhr.open(method.name, url, true)
		xhr.responseType = XMLHttpRequestResponseType.ARRAYBUFFER

		xhr.onload = { e ->
			//val u8array = Uint8Array(xhr.response as ArrayBuffer)
			//val out = ByteArray(u8array.length)
			//for (n in out.indices) out[n] = u8array[n]

			val out = Int8Array(xhr.response.unsafeCast<ArrayBuffer>()).unsafeCast<ByteArray>()

			//js("debugger;")
			deferred.complete(
				Response(
					status = xhr.status.toInt(),
					statusText = xhr.statusText,
					headers = Http.Headers(xhr.getAllResponseHeaders()),
					content = out.openAsync()
				)
			)
		}

		xhr.onerror = { e ->
			deferred.completeExceptionally(kotlin.RuntimeException("Error status=${xhr.status},'${xhr.statusText}' opening $url"))
		}

		for (header in headers) {
			val hnname = header.first.toLowerCase().trim()
			when (hnname) {
				"connection", "content-length" -> Unit // Refused to set unsafe header
				else -> xhr.setRequestHeader(header.first, header.second)
			}
		}

		deferred.invokeOnCompletion {
			if (deferred.isCancelled) {
				xhr.abort()
			}
		}

		if (content != null) {
			xhr.send(content.readAll())
		} else {
			xhr.send()
		}
		return deferred.await()
	}
}


class JsWebSocketClientFactory : WebSocketClientFactory() {
	override suspend fun create(
		url: String,
		protocols: List<String>?,
		origin: String?,
		wskey: String?,
		debug: Boolean
	): WebSocketClient = JsWebSocketClient(url, protocols, DEBUG = debug).apply { init() }
}

class JsWebSocketClient(url: String, protocols: List<String>?, val DEBUG: Boolean) :
	WebSocketClient(url, protocols, true) {
	val jsws = if (protocols != null) {
		WebSocket(url, arrayOf(*protocols.toTypedArray()))
	} else {
		WebSocket(url)
	}.apply {
		this.binaryType = BinaryType.ARRAYBUFFER
		this.addEventListener("open", { onOpen(Unit) })
		this.addEventListener("close", { e ->
			val event = e as CloseEvent
			var code = event.code.toInt()
			var reason = event.reason
			var wasClean = event.wasClean
			onClose(Unit)
		})
		this.addEventListener("message", { e ->
			val event = e as MessageEvent
			val data = event.data
			if (DEBUG) println("[WS-RECV]: $data :: stringListeners=${onStringMessage.listenerCount}, binaryListeners=${onBinaryMessage.listenerCount}, anyListeners=${onAnyMessage.listenerCount}")
			if (data is String) {
				val js = data
				onStringMessage(js)
				onAnyMessage(js)
			} else {
				val jb = data

				//onBinaryMessage(jb)
				//onAnyMessage(jb)
				TODO("onBinaryMessage, onAnyMessage")
			}
		})
	}

	suspend fun init() {
		if (DEBUG) println("[WS] Wait connection ($url)...")
		onOpen.waitOne()
		if (DEBUG) println("[WS] Connected!")
	}

	override fun close(code: Int, reason: String) {
		//jsws.methods["close"](code, reason)
		jsws.close()
	}

	override suspend fun send(message: String) {
		if (DEBUG) println("[WS-SEND]: $message")
		jsws.send(message)
	}

	override suspend fun send(message: ByteArray) {
		if (DEBUG) println("[WS-SEND]: ${message.toList()}")
		val bb = Int8Array(message.size)
		for (n in message.indices) bb[n] = message[n]
		jsws.send(bb)
	}
}
