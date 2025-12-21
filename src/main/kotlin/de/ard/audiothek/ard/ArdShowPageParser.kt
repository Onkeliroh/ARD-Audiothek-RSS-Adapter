package de.ard.audiothek.ard

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class ArdShowPageParser(
    private val objectMapper: ObjectMapper,
    private val basePageUrl: String = "https://www.ardaudiothek.de"
) {
    fun parse(html: String): ShowDetails {
        val document = Jsoup.parse(html)
        val payload = extractNextData(document)
        val resultNode = payload
            .path("props")
            .path("pageProps")
            .path("initialData")
            .path("data")
            .path("result")
        if (resultNode.isMissingNode) {
            throw ShowParsingException("Could not locate show data in __NEXT_DATA__ payload")
        }

        val title = resultNode.path("title").asText("")
        val description = resultNode.path("description").asTrimmedTextOrNull()
        val path = resultNode.path("path").asText("")
        val imageUrl = extractImageUrl(resultNode.path("image"))

        val episodes = resultNode.path("items").path("nodes")
            .mapNotNull { node -> mapEpisode(node) }
        val hasMore = resultNode.path("items").path("pageInfo").path("hasNextPage").asBoolean(false)

        return ShowDetails(
            id = resultNode.path("coreId").asText(resultNode.path("id").asText("")),
            title = title,
            description = description?.trim().takeUnless { it.isNullOrBlank() },
            canonicalUrl = buildCanonicalUrl(path),
            imageUrl = imageUrl,
            episodes = episodes,
            hasMoreEpisodes = hasMore
        )
    }

    private fun mapEpisode(node: JsonNode): EpisodeDetails? {
        val title = node.path("title").asText(null) ?: return null
        val linkPath = node.path("path").asText(null) ?: return null
        val summary = node.path("summary").asTrimmedTextOrNull()
        val publishDate = node.path("publishDate").asText(null)?.let { parseInstant(it) }
        val durationSeconds = node.path("duration")?.takeIf { it.isNumber }?.asLong()
        val imageUrl = extractImageUrl(node.path("image"))
        val audioNode = node.path("audios").takeIf { it.isArray && it.size() > 0 }?.get(0)
        val audio = audioNode?.let {
            AudioAsset(
                url = it.path("url").asText(""),
                mimeType = it.path("mimeType").asText(null),
                downloadUrl = it.path("downloadUrl").asText(null),
                lengthBytes = it.path("fileSize").takeIf { sizeNode -> sizeNode.isNumber }?.asLong()
            )
        }

        return EpisodeDetails(
            id = node.path("coreId").asText(node.path("id").asText("")),
            title = title,
            summary = summary,
            publishDate = publishDate,
            durationSeconds = durationSeconds,
            audio = audio,
            link = buildCanonicalUrl(linkPath),
            imageUrl = imageUrl
        )
    }

    private fun extractNextData(document: Document): JsonNode {
        val script = document.selectFirst("script#__NEXT_DATA__")
            ?: throw ShowParsingException("__NEXT_DATA__ script tag is missing from the ARD Audiothek page")
        val payload = script.data()
        if (payload.isNullOrBlank()) {
            throw ShowParsingException("__NEXT_DATA__ payload is empty")
        }
        return objectMapper.readTree(payload)
    }

    private fun parseInstant(raw: String): Instant? = try {
        OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (ex: Exception) {
        null
    }

    private fun buildCanonicalUrl(path: String): String {
        if (path.isBlank()) return basePageUrl
        val sanitized = path.removePrefix("/")
        return buildString {
            append(basePageUrl.trimEnd('/'))
            append('/')
            append(sanitized)
            if (!sanitized.endsWith('/')) {
                append('/')
            }
        }
    }

    private fun JsonNode.asTrimmedTextOrNull(): String? {
        val value = this.asText("").trim()
        return value.ifBlank { null }
    }

    private fun extractImageUrl(imageNode: JsonNode): String? {
        val prioritized = listOf(
            imageNode.path("url1X1").asText(""),
            imageNode.path("url").asText("")
        ).firstOrNull { it.isNotBlank() }
        return prioritized?.replace("{width}", DEFAULT_IMAGE_WIDTH)
    }

    companion object {
        private const val DEFAULT_IMAGE_WIDTH = "512"
    }
}

class ShowParsingException(message: String) : RuntimeException(message)
