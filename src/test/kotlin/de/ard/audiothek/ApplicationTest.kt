package de.ard.audiothek

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rometools.rome.io.SyndFeedInput
import de.ard.audiothek.ard.ArdShowPageParser
import de.ard.audiothek.ard.ShowPageClient
import de.ard.audiothek.rss.RssFeedBuilder
import de.ard.audiothek.rss.RssFeedCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.io.StringReader
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.text.Charsets

class ApplicationTest {
    @Test
    fun `root responds with status text`() = testApplication {
        application {
            module()
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ARD Audiothek RSS Mapper"))
    }

    @Test
    fun `health endpoint responds with OK`() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ARD Audiothek RSS Adapter is running.", response.bodyAsText())
    }

    @Test
    fun `rss feed endpoint returns cached syndication document`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        val sampleHtml = this::class.java.getResource("/Jagd auf Fantomas.html")?.readText()
            ?: error("Jagd auf Fantomas.html test resource missing")
        val parser = ArdShowPageParser(jacksonObjectMapper())
        val parsedShow = parser.parse(sampleHtml)
        assertEquals("Jagd auf Fantomas | ARD Hörspiel-Serie", parsedShow.title)
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = sampleHtml,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Text.Html.withCharset(Charsets.UTF_8).toString()
                )
            )
        }
        val httpClient = HttpClient(engine)
        val showClient = ShowPageClient(httpClient, parser)
        val rssBuilder = RssFeedBuilder()
        val rssCache = RssFeedCache(Duration.ofHours(1))

        application {
            module(
                ModuleDependencies(
                    httpClient = httpClient,
                    parser = parser,
                    showClient = showClient,
                    rssBuilder = rssBuilder,
                    rssCache = rssCache
                )
            )
        }

        val firstResponse = client.get("/rss/feed/urn:ard:show:ef3205b54d97da0e")
        val firstBody = firstResponse.bodyAsText()
        assertEquals(HttpStatusCode.OK, firstResponse.status, "body: $firstBody")
        val feed = SyndFeedInput().build(StringReader(firstBody))
        assertEquals("Jagd auf Fantomas | ARD Hörspiel-Serie", feed.title)
        assertEquals(12, feed.entries.size)

        val secondResponse = client.get("/rss/feed/urn:ard:show:ef3205b54d97da0e")
        val secondBody = secondResponse.bodyAsText()
        assertEquals(HttpStatusCode.OK, secondResponse.status, "body: $secondBody")
        assertEquals(firstBody, secondBody)
        assertEquals(1, requestCount)
    }

    @Test
    fun `rss feed bubbling of upstream failures`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        val parser = ArdShowPageParser(jacksonObjectMapper())
        val engine = MockEngine {
            respond(
                content = "upstream-error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(engine)
        val showClient = ShowPageClient(httpClient, parser)
        val rssCache = RssFeedCache(Duration.ofMinutes(5))

        application {
            module(
                ModuleDependencies(
                    httpClient = httpClient,
                    parser = parser,
                    showClient = showClient,
                    rssBuilder = RssFeedBuilder(),
                    rssCache = rssCache
                )
            )
        }

        val response = client.get("/rss/feed/problem")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("Failed to fetch show page"))
    }

    @Test
    fun `rss feed returns 500 when parser fails`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        val parser = ArdShowPageParser(jacksonObjectMapper())
        val engine = MockEngine {
            respond(
                content = "<html><body>No NEXT data</body></html>",
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Text.Html.withCharset(Charsets.UTF_8).toString()
                )
            )
        }
        val httpClient = HttpClient(engine)
        val showClient = ShowPageClient(httpClient, parser)

        application {
            module(
                ModuleDependencies(
                    httpClient = httpClient,
                    parser = parser,
                    showClient = showClient,
                    rssBuilder = RssFeedBuilder(),
                    rssCache = RssFeedCache(Duration.ofMinutes(5))
                )
            )
        }

        val response = client.get("/rss/feed/trigger-parser-error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.bodyAsText().contains("script tag is missing"))
    }
}
