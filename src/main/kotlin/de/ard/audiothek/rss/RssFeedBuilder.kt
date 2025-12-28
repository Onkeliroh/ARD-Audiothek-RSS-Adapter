package de.ard.audiothek.rss

import com.rometools.modules.itunes.EntryInformation
import com.rometools.modules.itunes.EntryInformationImpl
import com.rometools.modules.itunes.FeedInformation
import com.rometools.modules.itunes.FeedInformationImpl
import com.rometools.modules.itunes.types.Category
import com.rometools.rome.feed.module.Module
import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.SyndFeedOutput
import de.ard.audiothek.ard.EpisodeDetails
import de.ard.audiothek.ard.ShowDetails
import org.jdom2.Element
import org.jdom2.Namespace
import java.util.*

class RssFeedBuilder {
    companion object {
        private val ITUNES_NS = Namespace.getNamespace("itunes", "http://www.itunes.com/dtds/podcast-1.0.dtd")
        private const val DEFAULT_AUTHOR = "ARD Audiothek"
        private const val DEFAULT_OWNER_EMAIL = "info@ard-audiothek.de"
    }

    fun build(show: ShowDetails): String {
        val feed = SyndFeedImpl().apply {
            feedType = "rss_2.0"
            title = show.title
            link = show.canonicalUrl
            description = show.description ?: "Episodenfeed aus der ARD Audiothek"
            language = "de-DE"
            publishedDate = show.episodes.mapNotNull { it.publishDate }.maxOrNull()?.let { Date.from(it) }
            
            // Add channel image if available
            show.imageUrl?.let {
                image = SyndImageImpl().apply {
                    url = it
                    title = show.title
                    link = show.canonicalUrl
                }
            }
            
            // Add iTunes podcast metadata
            modules = mutableListOf<Module>(createItunesFeedModule(show))
            
            entries = show.episodes.map { it.toSyndEntry() }
        }
        return SyndFeedOutput().outputString(feed)
    }
    
    private fun createItunesFeedModule(show: ShowDetails): FeedInformation {
        return FeedInformationImpl().apply {
            author = DEFAULT_AUTHOR
            ownerName = DEFAULT_AUTHOR
            ownerEmailAddress = DEFAULT_OWNER_EMAIL
            show.imageUrl?.let { image = java.net.URI(it).toURL() }
            summary = show.description ?: "Episodenfeed aus der ARD Audiothek"
            
            // Set default category - can be customized based on show metadata
            categories = listOf(Category().apply {
                name = "Society & Culture"
            })
            
            // Assume clean content unless specified otherwise
            explicit = false
        }
    }

    private fun EpisodeDetails.toSyndEntry(): SyndEntryImpl = SyndEntryImpl().apply {
        title = this@toSyndEntry.title
        link = this@toSyndEntry.link
        
        // GUID is required by RSS 2.0 and Apple Podcasts - must be globally unique and never change
        uri = this@toSyndEntry.id
        
        publishedDate = this@toSyndEntry.publishDate?.let { Date.from(it) }
        description = buildDescription(this@toSyndEntry)
        
        // Add iTunes episode metadata
        modules = mutableListOf<Module>(createItunesEntryModule(this@toSyndEntry))
        
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
    
    private fun createItunesEntryModule(episode: EpisodeDetails): EntryInformation {
        return EntryInformationImpl().apply {
            author = DEFAULT_AUTHOR
            summary = episode.summary
            episode.durationSeconds?.let { 
                // Duration expects milliseconds as Long
                duration = com.rometools.modules.itunes.types.Duration(it * 1000L)
            }
            episode.imageUrl?.let {
                try {
                    image = java.net.URI(it).toURL()
                } catch (e: Exception) {
                    // Ignore invalid URLs
                }
            }
            // Assume clean content unless specified otherwise
            explicit = false
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
