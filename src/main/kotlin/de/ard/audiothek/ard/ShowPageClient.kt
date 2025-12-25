package de.ard.audiothek.ard

import de.ard.audiothek.FeedUrlValidator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class ShowPageClient(
    private val httpClient: HttpClient,
    private val parser: ArdShowPageParser
) {
    suspend fun fetchShow(pageUrl: String): ShowDetails {
        val normalizedUrl = FeedUrlValidator.normalize(pageUrl)
        val response = httpClient.get(normalizedUrl) {
            header(HttpHeaders.Accept, ContentType.Text.Html.withCharset(Charsets.UTF_8).toString())
            header(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9")
        }
        ensureSuccess(response, normalizedUrl)
        val html = response.bodyAsText()
        return parser.parse(html)
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
