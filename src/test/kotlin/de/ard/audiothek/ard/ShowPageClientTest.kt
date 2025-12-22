package de.ard.audiothek.ard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlin.text.Charsets

class ShowPageClientTest {
    private val parser = ArdShowPageParser(jacksonObjectMapper())
    private val sampleHtml: String = this::class.java.getResource("/Jagd auf Fantomas.html")?.readText()
        ?: error("Jagd auf Fantomas.html test resource missing")

    @Test
    fun `fetchShow resolves urn ids and forwards headers`() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val engine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = sampleHtml,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Text.Html.withCharset(Charsets.UTF_8).toString()
                )
            )
        }
        val httpClient = HttpClient(engine)
        val client = ShowPageClient(httpClient, parser)

        val show = client.fetchShow("urn:ard:show:ef3205b54d97da0e")

        assertEquals("Jagd auf Fantomas | ARD Hörspiel-Serie", show.title)
        val request = requireNotNull(capturedRequest)
        assertEquals("https://www.ardaudiothek.de/sendung/urn:ard:show:ef3205b54d97da0e/", request.url.toString())
        assertTrue(request.headers[HttpHeaders.Accept]?.contains("text/html") == true)
        assertEquals("de-DE,de;q=0.9", request.headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun `fetchShow throws ShowRetrievalException for non-success responses`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "nope",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(engine)
        val client = ShowPageClient(httpClient, parser)

        val error = assertFailsWith<ShowRetrievalException> {
            client.fetchShow("/sendung/jagd-auf-fantomas-ard-hoerspiel-serie/urn:ard:show:ef3205b54d97da0e/")
        }
        assertTrue(error.message!!.contains("502"))
    }

    @Test
    fun `fetchShow leaves absolute URLs untouched`() = runBlocking {
        var requestedUrl: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = sampleHtml,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Text.Html.withCharset(Charsets.UTF_8).toString()
                )
            )
        }
        val httpClient = HttpClient(engine)
        val client = ShowPageClient(httpClient, parser)

        val absoluteUrl = "https://audio.example.org/custom-feed/"
        client.fetchShow(absoluteUrl)

        assertEquals(absoluteUrl, requestedUrl)
    }

    @Test
    fun `fetchShow resolves bare identifiers to canonical path`() = runBlocking {
        var requestedUrl: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = sampleHtml,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Text.Html.withCharset(Charsets.UTF_8).toString()
                )
            )
        }
        val httpClient = HttpClient(engine)
        val client = ShowPageClient(httpClient, parser)

        client.fetchShow("science-podcast")

        assertEquals("https://www.ardaudiothek.de/sendung/science-podcast/", requestedUrl)
    }
}
