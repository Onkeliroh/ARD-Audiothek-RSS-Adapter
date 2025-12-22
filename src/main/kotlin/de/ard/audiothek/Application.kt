package de.ard.audiothek

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.ard.audiothek.ard.ArdShowPageParser
import de.ard.audiothek.ard.ShowPageClient
import de.ard.audiothek.ard.ShowParsingException
import de.ard.audiothek.ard.ShowRetrievalException
import de.ard.audiothek.rss.RssFeedBuilder
import de.ard.audiothek.rss.RssFeedCache
import de.ard.audiothek.ui.feedMapperPage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.html.respondHtml
import com.typesafe.config.ConfigFactory
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.text.Charsets

fun main() {
    val config = HoconApplicationConfig(ConfigFactory.load())
    val host = config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    val port = resolvePort(config)
    val environment = applicationEngineEnvironment {
        this.config = config
        log = LoggerFactory.getLogger("Application")
        module {
            module()
        }
        connector {
            this.host = host
            this.port = port
        }
    }
    val displayHost = if (host == "0.0.0.0") "localhost" else host
    environment.monitor.subscribe(ApplicationStarted) {
        environment.log.info("Server ready: http://$displayHost:$port/")
    }
    embeddedServer(Netty, environment).start(wait = true)
}

private fun resolvePort(config: ApplicationConfig): Int {
    val envPort = System.getenv("PORT")?.toIntOrNull()
    if (envPort != null) {
        return envPort
    }
    return config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080
}

@Suppress("unused")
fun Application.module(dependencies: ModuleDependencies = ModuleDependencies.create(this)) {
    val httpClient = dependencies.httpClient
    val showClient = dependencies.showClient
    val rssBuilder = dependencies.rssBuilder
    val rssCache = dependencies.rssCache

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
            call.respondHtml(HttpStatusCode.OK) {
                feedMapperPage()
            }
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

private fun Application.resolveRssCacheTtl(): Duration {
    val defaultTtl = Duration.ofHours(6)
    val configValue = environment.config.propertyOrNull("audiothek.rssCache.ttlSeconds")?.getString()
    val seconds = configValue?.toLongOrNull()
    return seconds?.takeIf { it > 0 }?.let { Duration.ofSeconds(it) } ?: defaultTtl
}

data class ModuleDependencies(
    val httpClient: HttpClient,
    val parser: ArdShowPageParser,
    val showClient: ShowPageClient,
    val rssBuilder: RssFeedBuilder,
    val rssCache: RssFeedCache
) {
    companion object {
        fun create(
            application: Application,
            engine: HttpClientEngine = CIO.create()
        ): ModuleDependencies {
            val httpClient = HttpClient(engine) {
                expectSuccess = false
            }
            val parser = ArdShowPageParser(jacksonObjectMapper())
            val showClient = ShowPageClient(httpClient, parser)
            val rssBuilder = RssFeedBuilder()
            val rssCache = RssFeedCache(application.resolveRssCacheTtl())
            return ModuleDependencies(httpClient, parser, showClient, rssBuilder, rssCache)
        }
    }
}
