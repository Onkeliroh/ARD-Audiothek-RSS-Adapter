package de.ard.audiothek.ard

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class ShowPageClient(
    private val httpClient: HttpClient,
    private val parser: ArdShowPageParser,
    private val pageBaseUrl: String = "https://www.ardaudiothek.de"
) {
    suspend fun fetchShow(feedId: String): ShowDetails {
        val url = resolvePageUrl(feedId)
        val response = httpClient.get(url) {
            header(HttpHeaders.Accept, ContentType.Text.Html.withCharset(Charsets.UTF_8).toString())
            header(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9")
        }
        ensureSuccess(response, url)
        val html = response.bodyAsText()
        return parser.parse(html)
    }

    private fun resolvePageUrl(feedId: String): String {
        val normalizedBase = pageBaseUrl.trimEnd('/')
        return when {
            feedId.startsWith("http://", ignoreCase = true) || feedId.startsWith(
                "https://",
                ignoreCase = true
            ) -> feedId

            feedId.startsWith("/") -> normalizedBase + feedId
            feedId.startsWith("urn:") -> buildString {
                append(normalizedBase)
                append("/sendung/")
                append(feedId.trimEnd('/'))
                append('/')
            }

            else -> buildString {
                append(normalizedBase)
                append("/sendung/")
                append(feedId.trim('/'))
                append('/')
            }
        }
    }

    private suspend fun ensureSuccess(response: HttpResponse, url: String) {
        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            throw ShowRetrievalException(
                "Failed to fetch show page $url : ${response.status.value} ${response.status.description}. Body: ${
                    body?.take(
                        256
                    )
                }"
            )
        }
    }
}

class ShowRetrievalException(message: String) : RuntimeException(message)
