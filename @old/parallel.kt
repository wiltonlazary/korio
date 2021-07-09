package com.soywiz.korio.async

import kotlinx.coroutines.*

class ParallelContext(val scope: CoroutineScope) : CoroutineScope by scope

suspend fun CoroutineScope.parallel(callback: ParallelContext.() -> Unit) {
	val res = launch { callback(ParallelContext(this@parallel)) }
	res.join()
}

fun CoroutineScope.sequence(callback: suspend ParallelContext.() -> Unit) {
	async { callback(ParallelContext(this@sequence)) }
}

fun ParallelContext.sequence(callback: suspend ParallelContext.() -> Unit) {
	async { callback(this@sequence) }
}

