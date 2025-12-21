package de.ard.audiothek.ard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ArdShowPageParserTest {
    private val parser = ArdShowPageParser(jacksonObjectMapper(), basePageUrl = "https://www.ardaudiothek.de")

    @Test
    fun `parses show details from NEXT data`() {
        val html = this::class.java.getResource("/sample-show.html")?.readText()
            ?: error("sample-show.html test resource missing")

        val show = parser.parse(html)

        assertEquals("Sample Show", show.title)
        assertEquals("https://www.ardaudiothek.de/sendung/sample-show/urn:ard:show:sample/", show.canonicalUrl)
        assertFalse(show.hasMoreEpisodes)
        assertEquals(2, show.episodes.size)

        val first = show.episodes.first()
        assertEquals("Episode One", first.title)
        assertEquals("https://www.ardaudiothek.de/episode/urn:ard:episode:1/", first.link)
        assertNotNull(first.audio)
        assertEquals("https://cdn.example.com/audio-one.mp3", first.audio?.url)
    }
}
