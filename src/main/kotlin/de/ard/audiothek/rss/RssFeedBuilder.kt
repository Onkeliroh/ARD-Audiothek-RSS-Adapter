package de.ard.audiothek.rss

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.SyndFeedOutput
import de.ard.audiothek.ard.EpisodeDetails
import de.ard.audiothek.ard.ShowDetails
import java.util.*

class RssFeedBuilder {
    fun build(show: ShowDetails): String {
        val feed = SyndFeedImpl().apply {
            feedType = "rss_2.0"
            title = show.title
            link = show.canonicalUrl
            description = show.description ?: "Episodenfeed aus der ARD Audiothek"
            language = "de-DE"
            publishedDate = show.episodes.mapNotNull { it.publishDate }.maxOrNull()?.let { Date.from(it) }
            entries = show.episodes.map { it.toSyndEntry() }
        }
        return SyndFeedOutput().outputString(feed)
    }

    private fun EpisodeDetails.toSyndEntry(): SyndEntryImpl = SyndEntryImpl().apply {
        title = this@toSyndEntry.title
        link = this@toSyndEntry.link
        uri = this@toSyndEntry.id
        publishedDate = this@toSyndEntry.publishDate?.let { Date.from(it) }
        description = buildDescription(this@toSyndEntry)
        enclosures = buildList {
            this@toSyndEntry.audio?.let { audio ->
                if (audio.url.isNotBlank()) {
                    add(SyndEnclosureImpl().apply {
                        url = audio.downloadUrl ?: audio.url
                        type = audio.mimeType ?: "audio/mpeg"
                        length = audio.lengthBytes ?: 0
                    })
                }
            }
        }
    }

    private fun buildDescription(episode: EpisodeDetails): SyndContent = SyndContentImpl().apply {
        type = "text/html"
        value = buildString {
            episode.imageUrl?.let {
                append('<').append('p').append('>').append("<img src=\"").append(it)
                    .append("\" alt=\"Episode cover\" loading=\"lazy\" />").append("</p>")
            }
            episode.summary?.let { append("<p>").append(it).append("</p>") }
            episode.durationSeconds?.let { duration ->
                append("<p><strong>Spielzeit:</strong> ")
                append(formatDuration(duration))
                append("</p>")
            }
        }.ifEmpty { "<p>${episode.summary ?: "Keine Beschreibung verfuegbar."}</p>" }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append(h).append('h').append(' ')
            if (m > 0 || h > 0) append(m).append('m').append(' ')
            append(s).append('s')
        }.trim()
    }
}
