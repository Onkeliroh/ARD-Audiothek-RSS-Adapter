package de.ard.audiothek.rss

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class RssFeedCache(private val ttl: Duration) {
    private data class Entry(val value: String, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: String): String? {
        val entry = entries[key] ?: return null
        return if (entry.expiresAt.isAfter(Instant.now())) {
            entry.value
        } else {
            entries.remove(key, entry)
            null
        }
    }

    suspend fun getOrPut(key: String, loader: suspend () -> String): String {
        val cached = get(key)
        if (cached != null) {
            return cached
        }
        val value = loader()
        val expiresAt = Instant.now().plus(ttl)
        entries[key] = Entry(value, expiresAt)
        return value
    }

    fun clear() {
        entries.clear()
    }
}
