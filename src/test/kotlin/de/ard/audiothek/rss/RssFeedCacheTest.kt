package de.ard.audiothek.rss

import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RssFeedCacheTest {
    @Test
    fun `returns cached value while entry is fresh`() = runBlocking {
        val cache = RssFeedCache(Duration.ofSeconds(60))
        var counter = 0
        val value1 = cache.getOrPut("foo") {
            counter += 1
            "value-$counter"
        }
        val value2 = cache.getOrPut("foo") {
            counter += 1
            "value-$counter"
        }
        assertEquals("value-1", value1)
        assertEquals("value-1", value2)
        assertEquals(1, counter)
    }

    @Test
    fun `refreshes entry after ttl expires`() = runBlocking {
        val cache = RssFeedCache(Duration.ofMillis(50))
        var counter = 0
        val first = cache.getOrPut("foo") {
            counter += 1
            "value-$counter"
        }
        delay(60)
        val second = cache.getOrPut("foo") {
            counter += 1
            "value-$counter"
        }
        assertTrue(first != second)
        assertEquals(2, counter)
    }
}
