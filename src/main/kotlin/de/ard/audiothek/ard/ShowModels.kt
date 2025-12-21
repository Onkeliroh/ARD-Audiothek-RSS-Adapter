package de.ard.audiothek.ard

import java.time.Instant

/** Domain view of a show page returned by the ARD Audiothek. */
data class ShowDetails(
    val id: String,
    val title: String,
    val description: String?,
    val canonicalUrl: String,
    val imageUrl: String?,
    val episodes: List<EpisodeDetails>,
    val hasMoreEpisodes: Boolean
)

data class EpisodeDetails(
    val id: String,
    val title: String,
    val summary: String?,
    val publishDate: Instant?,
    val durationSeconds: Long?,
    val audio: AudioAsset?,
    val link: String,
    val imageUrl: String?
)

data class AudioAsset(
    val url: String,
    val mimeType: String?,
    val downloadUrl: String?,
    val lengthBytes: Long?
)
