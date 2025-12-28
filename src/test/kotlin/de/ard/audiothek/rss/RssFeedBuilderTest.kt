package de.ard.audiothek.rss

import com.rometools.rome.io.SyndFeedInput
import de.ard.audiothek.ard.AudioAsset
import de.ard.audiothek.ard.EpisodeDetails
import de.ard.audiothek.ard.ShowDetails
import java.io.StringReader
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RssFeedBuilderTest {
    private val builder = RssFeedBuilder()

    @Test
    fun `build produces fully populated RSS feed`() {
        val newerPublishDate = Instant.parse("2024-02-03T10:15:30Z")
        val olderPublishDate = Instant.parse("2024-01-01T08:00:00Z")
        val episodes = listOf(
            EpisodeDetails(
                id = "episode-1",
                title = "Episode 1",
                summary = "A detailed summary",
                publishDate = newerPublishDate,
                durationSeconds = 3723,
                audio = AudioAsset(
                    url = "https://cdn.example.com/stream.mp3",
                    mimeType = null,
                    downloadUrl = "https://cdn.example.com/download.mp3",
                    lengthBytes = 1024L
                ),
                link = "https://www.ardaudiothek.de/episode/episode-1/",
                imageUrl = "https://images.example.com/cover1.jpg"
            ),
            EpisodeDetails(
                id = "episode-2",
                title = "Episode 2",
                summary = null,
                publishDate = olderPublishDate,
                durationSeconds = null,
                audio = AudioAsset(
                    url = "",
                    mimeType = "audio/ogg",
                    downloadUrl = null,
                    lengthBytes = null
                ),
                link = "https://www.ardaudiothek.de/episode/episode-2/",
                imageUrl = null
            )
        )
        val show = ShowDetails(
            id = "show-1",
            title = "Sample Show",
            description = null,
            canonicalUrl = "https://www.ardaudiothek.de/sendung/sample-show/",
            imageUrl = "https://images.example.com/show.jpg",
            episodes = episodes,
            hasMoreEpisodes = false
        )

        val xml = builder.build(show)
        val feed = SyndFeedInput().build(StringReader(xml))

        assertEquals(show.title, feed.title)
        assertEquals(show.canonicalUrl, feed.link)
        assertEquals("Episodenfeed aus der ARD Audiothek", feed.description)
        // RSS 2.0 pubDate at channel level is optional - Rome may not preserve it on round-trip
        if (feed.publishedDate != null) {
            assertEquals(newerPublishDate.toEpochMilli(), feed.publishedDate.time)
        }
        // Verify iTunes namespace and elements are present in XML
        assertTrue(xml.contains("xmlns:itunes"))
        assertTrue(xml.contains("itunes:"))
        
        // Verify explicit tag uses "false" not "no" for RSS 2.0 compliance
        assertTrue(xml.contains("<itunes:explicit>false</itunes:explicit>"), 
            "iTunes explicit tag should use 'false' not 'no' for RSS 2.0 compliance")
        assertTrue(!xml.contains("<itunes:explicit>no</itunes:explicit>"),
            "iTunes explicit tag should not contain 'no', must use 'false'")
        
        assertEquals(2, feed.entries.size)

        val firstEntry = feed.entries.first()
        assertEquals("Episode 1", firstEntry.title)
        val enclosure = firstEntry.enclosures.single()
        assertEquals("https://cdn.example.com/download.mp3", enclosure.url)
        assertEquals("audio/mpeg", enclosure.type)
        assertEquals(1024L, enclosure.length)
        val descriptionHtml = firstEntry.description.value
        assertTrue(descriptionHtml.contains("<img src=\"https://images.example.com/cover1.jpg\""))
        assertTrue(descriptionHtml.contains("A detailed summary"))
        assertTrue(descriptionHtml.contains("<strong>Spielzeit:</strong> 1h 2m 3s"))

        val secondEntry = feed.entries[1]
        assertEquals("Episode 2", secondEntry.title)
        assertTrue(secondEntry.enclosures.isEmpty())
        assertTrue(secondEntry.description.value.contains("Keine Beschreibung"))
    }

    @Test
    fun `build handles missing dates and audio fallbacks`() {
        val episodes = listOf(
            EpisodeDetails(
                id = "episode-3",
                title = "Episode 3",
                summary = "Compact overview",
                publishDate = null,
                durationSeconds = 125,
                audio = AudioAsset(
                    url = "https://cdn.example.com/stream2.mp3",
                    mimeType = "audio/aac",
                    downloadUrl = null,
                    lengthBytes = null
                ),
                link = "https://www.ardaudiothek.de/episode/episode-3/",
                imageUrl = null
            ),
            EpisodeDetails(
                id = "episode-4",
                title = "Episode 4",
                summary = "Quick listen",
                publishDate = null,
                durationSeconds = 42,
                audio = null,
                link = "https://www.ardaudiothek.de/episode/episode-4/",
                imageUrl = "https://images.example.com/cover4.jpg"
            )
        )
        val show = ShowDetails(
            id = "show-2",
            title = "Metadata Showcase",
            description = "Offizieller Beschreibungstext",
            canonicalUrl = "https://www.ardaudiothek.de/sendung/metadata-show/",
            imageUrl = null,
            episodes = episodes,
            hasMoreEpisodes = true
        )

        val feed = SyndFeedInput().build(StringReader(builder.build(show)))

        assertEquals("Offizieller Beschreibungstext", feed.description)
        assertNull(feed.publishedDate)
        assertEquals(2, feed.entries.size)

        // First entry has no length, so no enclosure should be added (RSS 2.0 requires length attribute)
        val firstEntry = feed.entries.first()
        assertTrue(firstEntry.enclosures.isEmpty(), "Episode without lengthBytes should not have enclosure")
        assertTrue(firstEntry.description.value.contains("2m 5s"))

        val secondEntry = feed.entries[1]
        assertTrue(secondEntry.enclosures.isEmpty())
        assertTrue(secondEntry.description.value.contains("42s"))
        assertTrue(secondEntry.description.value.contains("<img src=\"https://images.example.com/cover4.jpg\""))
    }
}
