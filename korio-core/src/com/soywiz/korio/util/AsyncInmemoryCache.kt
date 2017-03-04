package com.soywiz.korio.util

import com.soywiz.korio.async.Promise
import com.soywiz.korio.async.async
import com.soywiz.korio.inject.Prototype

@Prototype
class AsyncInmemoryCache {
	data class Entry(val timestamp: Long, val data: Promise<Any?>)

	val cache = hashMapOf<String, AsyncInmemoryCache.Entry?>()

	fun <T : Any?> get(clazz: Class<T>, key: String, ttlMs: Int) = AsyncInmemoryEntry(clazz, this, key, ttlMs)

	//fun <T : Any?> getTyped(clazz: Class<T>, key: String = clazz, ttl: TimeSpan) = AsyncInmemoryEntry(clazz, this, key, ttl)

	@Suppress("UNCHECKED_CAST")
	suspend fun <T : Any?> get(key: String, ttlMs: Int, gen: suspend () -> T): T {
		val entry = cache[key]
		if (entry == null || (System.currentTimeMillis() - entry.timestamp) >= ttlMs) {
			cache[key] = AsyncInmemoryCache.Entry(System.currentTimeMillis(), async(gen) as Promise<Any?>)
		}
		return (cache[key]!!.data as Promise<T>).await()
	}

	//suspend fun <T : Any?> get(key: String, ttl: TimeSpan, gen: () -> Promise<T>) = await(getAsync(key, ttl, gen))
}

class AsyncInmemoryEntry<T : Any?>(val clazz: Class<T>, val cache: AsyncInmemoryCache, val key: String, val ttlMs: Int) {
	//fun getAsync(gen: () -> Promise<T>): Promise<T> = async { cache.get(key, ttl, gen) }

	suspend fun get(routine: suspend () -> T) = cache.get(key, ttlMs, routine)
}
