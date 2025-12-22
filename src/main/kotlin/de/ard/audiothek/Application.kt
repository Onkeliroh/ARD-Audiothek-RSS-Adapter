package de.ard.audiothek

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.ard.audiothek.ard.ArdShowPageParser
import de.ard.audiothek.ard.ShowPageClient
import de.ard.audiothek.ard.ShowParsingException
import de.ard.audiothek.ard.ShowRetrievalException
import de.ard.audiothek.rss.RssFeedBuilder
import de.ard.audiothek.rss.RssFeedCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import java.time.Duration
import kotlin.text.Charsets

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }
    val parser = ArdShowPageParser(jacksonObjectMapper())
    val showClient = ShowPageClient(httpClient, parser)
    val rssBuilder = RssFeedBuilder()
    val rssCacheTtl = resolveRssCacheTtl()
    val rssCache = RssFeedCache(rssCacheTtl)

    environment.monitor.subscribe(ApplicationStopped) { httpClient.close() }

    if (pluginOrNull(StatusPages) == null) {
        install(StatusPages) {
            exception<ShowRetrievalException> { call, cause ->
                call.respond(HttpStatusCode.BadGateway, cause.message ?: "Unable to fetch show page.")
            }
            exception<ShowParsingException> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Failed to parse ARD Audiothek data.")
            }
            exception<Throwable> { call, cause ->
                this@module.environment.log.error("Unhandled error while rendering RSS feed", cause)
                call.respond(HttpStatusCode.InternalServerError, "Unexpected server error.")
            }
        }
    }

    routing {
        get("/") {
            call.respondText(landingPageHtml, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }
        get("/health") {
            call.respondText("ARD Audiothek RSS Adapter is running.")
        }
        get("/rss/feed/{feedId}") {
            val feedId = call.parameters.getOrFail("feedId")
            val rss = rssCache.getOrPut(feedId) {
                val show = showClient.fetchShow(feedId)
                rssBuilder.build(show)
            }
            call.respondText(rss, ContentType.Application.Xml.withCharset(Charsets.UTF_8))
        }
    }
}

private val landingPageHtml: String by lazy {
    val resource = Application::class.java.classLoader.getResourceAsStream("feed-mapper.html")
        ?: error("feed-mapper.html is missing from resources")
    resource.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun Application.resolveRssCacheTtl(): Duration {
    val defaultTtl = Duration.ofHours(6)
    val configValue = environment.config.propertyOrNull("audiothek.rssCache.ttlSeconds")?.getString()
    val seconds = configValue?.toLongOrNull()
    return seconds?.takeIf { it > 0 }?.let { Duration.ofSeconds(it) } ?: defaultTtl
}
