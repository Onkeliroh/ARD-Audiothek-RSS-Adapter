package de.ard.audiothek.ard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

        @Test
        fun `parse handles fallback fields and filters invalid episodes`() {
                val html = wrapPayload(
                        """
                        {
                            "props": {
                                "pageProps": {
                                    "initialData": {
                                        "data": {
                                            "result": {
                                                "coreId": "urn:ard:show:fallback",
                                                "title": "Fallback",
                                                "description": "  Trim me  ",
                                                "path": "",
                                                "image": {
                                                    "url1X1": "https://images.example.com/base-{width}"
                                                },
                                                "items": {
                                                    "pageInfo": { "hasNextPage": true },
                                                    "nodes": [
                                                        { "id": "missing-title", "path": "/episode/ignored/" },
                                                        {
                                                            "coreId": "urn:ard:episode:ok",
                                                            "title": " Episode Trim ",
                                                            "summary": "  Short  ",
                                                            "publishDate": "not-a-date",
                                                            "duration": 180,
                                                            "path": "/episode/valid/",
                                                            "image": { "url": "https://images.example.com/ep-{width}" },
                                                            "audios": []
                                                        }
                                                    ]
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        """.trimIndent()
                )

                val show = parser.parse(html)

                assertEquals("Fallback", show.title)
                assertEquals("Trim me", show.description)
                assertEquals("https://www.ardaudiothek.de", show.canonicalUrl)
                assertEquals("https://images.example.com/base-512", show.imageUrl)
                assertTrue(show.hasMoreEpisodes)
                assertEquals(1, show.episodes.size)

                val episode = show.episodes.single()
                assertEquals(" Episode Trim ", episode.title)
                assertEquals("Short", episode.summary)
                assertNull(episode.publishDate)
                assertEquals(180, episode.durationSeconds)
                assertEquals("https://www.ardaudiothek.de/episode/valid/", episode.link)
                assertEquals("https://images.example.com/ep-512", episode.imageUrl)
                assertNull(episode.audio)
        }

        @Test
        fun `parse throws when NEXT data script is missing`() {
                val html = "<html><body><p>No script</p></body></html>"

                assertThrows(ShowParsingException::class.java) {
                        parser.parse(html)
                }
        }

        @Test
        fun `parse throws when result node is missing`() {
                val html = wrapPayload(
                        """
                        {
                            "props": {
                                "pageProps": {
                                    "initialData": {
                                        "data": { }
                                    }
                                }
                            }
                        }
                        """.trimIndent()
                )

                assertThrows(ShowParsingException::class.java) {
                        parser.parse(html)
                }
        }

        private fun wrapPayload(payload: String): String = """
                <html>
                    <body>
                        <script id="__NEXT_DATA__" type="application/json">
                            $payload
                        </script>
                    </body>
                </html>
        """.trimIndent()
}
