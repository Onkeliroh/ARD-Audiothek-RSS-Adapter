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
    fun `rss feed endpoint returns cached syndication document`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        val sampleHtml = this::class.java.getResource("/sample-show.html")?.readText()
            ?: error("sample-show.html test resource missing")
        val parser = ArdShowPageParser(jacksonObjectMapper())
        val parsedShow = parser.parse(sampleHtml)
        assertEquals("Sample Show", parsedShow.title)
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

        val firstResponse = client.get("/rss/feed/urn:ard:show:sample")
        val firstBody = firstResponse.bodyAsText()
        assertEquals(HttpStatusCode.OK, firstResponse.status, "body: $firstBody")
        val feed = SyndFeedInput().build(StringReader(firstBody))
        assertEquals("Sample Show", feed.title)
        assertEquals(2, feed.entries.size)

        val secondResponse = client.get("/rss/feed/urn:ard:show:sample")
        val secondBody = secondResponse.bodyAsText()
        assertEquals(HttpStatusCode.OK, secondResponse.status, "body: $secondBody")
        assertEquals(firstBody, secondBody)
        assertEquals(1, requestCount)
    }
}
