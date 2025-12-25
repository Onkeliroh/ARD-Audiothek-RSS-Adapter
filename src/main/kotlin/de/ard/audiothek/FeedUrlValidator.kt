package de.ard.audiothek

import java.net.URI

class InvalidFeedUrlException(message: String) : RuntimeException(message)

object FeedUrlValidator {
    fun normalize(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            throw InvalidFeedUrlException("Feed URL must not be empty.")
        }
        val uri = runCatching { URI(trimmed) }.getOrElse {
            throw InvalidFeedUrlException("Feed URL must be a valid URL.")
        }
        val scheme = uri.scheme?.lowercase() ?: throw InvalidFeedUrlException(
            "Feed URL must include http:// or https://."
        )
        if (scheme != "http" && scheme != "https") {
            throw InvalidFeedUrlException("Feed URL must start with http:// or https://.")
        }
        if (uri.host.isNullOrBlank()) {
            throw InvalidFeedUrlException("Feed URL must include a hostname.")
        }
        return uri.toString()
    }
}
