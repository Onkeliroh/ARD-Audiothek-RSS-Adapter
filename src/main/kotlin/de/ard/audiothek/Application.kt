package de.ard.audiothek

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.typesafe.config.ConfigFactory
import de.ard.audiothek.ard.ArdShowPageParser
import de.ard.audiothek.ard.ShowPageClient
import de.ard.audiothek.ard.ShowParsingException
import de.ard.audiothek.ard.ShowRetrievalException
import de.ard.audiothek.rss.RssFeedBuilder
import de.ard.audiothek.rss.RssFeedCache
import de.ard.audiothek.ui.errorPage
import de.ard.audiothek.ui.feedMapperPage
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import org.slf4j.LoggerFactory
import java.time.Duration

fun main() {
    val config = HoconApplicationConfig(ConfigFactory.load())
    val host = config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    val port = resolvePort(config)
    val environment = applicationEnvironment {
        this.config = config
        log = LoggerFactory.getLogger("Application")
    }
    val displayHost = if (host == "0.0.0.0") "localhost" else host
    val server = embeddedServer(
        Netty,
        serverConfig(environment) {
            module(Application::module)
        },
        configure = {
            connector {
                this.host = host
                this.port = port
            }
        }
    )
    server.application.monitor.subscribe(ApplicationStarted) {
        server.environment.log.info("Server ready: http://$displayHost:$port/")
    }
    server.start(wait = true)
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
    val appLogger = this.environment.log

    monitor.subscribe(ApplicationStopped) { httpClient.close() }

    if (pluginOrNull(StatusPages) == null) {
        install(StatusPages) {
            exception<ShowRetrievalException> { call, cause ->
                call.respond(HttpStatusCode.BadGateway, cause.message!!)
            }
            exception<ShowParsingException> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message!!)
            }
            exception<Throwable> { call, cause ->
                appLogger.error("Unhandled error while rendering RSS feed", cause)
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
        get("/rss/feed/{feedUrl...}") {
            val rawFeedUrl = call.parameters.getOrFail("feedUrl")
            val feedUrl = try {
                FeedUrlValidator.normalize(rawFeedUrl)
            } catch (ex: InvalidFeedUrlException) {
                call.respondRssError(HttpStatusCode.BadRequest, ex.message!!)
                return@get
            }

            try {
                val rss = rssCache.getOrPut(feedUrl) {
                    val show = showClient.fetchShow(feedUrl)
                    rssBuilder.build(show)
                }
                call.respondText(rss, ContentType.Application.Xml.withCharset(Charsets.UTF_8))
            } catch (ex: InvalidFeedUrlException) {
                call.respondRssError(HttpStatusCode.BadRequest, ex.message!!)
            } catch (ex: ShowRetrievalException) {
                call.respondRssError(HttpStatusCode.BadGateway, ex.message!!)
            } catch (ex: ShowParsingException) {
                call.respondRssError(
                    HttpStatusCode.InternalServerError,
                    ex.message!!
                )
            } catch (ex: Throwable) {
                appLogger.error("Unhandled error while rendering RSS feed for $feedUrl", ex)
                call.respondRssError(HttpStatusCode.InternalServerError, "Unexpected server error.")
            }
        }
    }
}

private suspend fun ApplicationCall.respondRssError(status: HttpStatusCode, details: String) {
    respondHtml(status) {
        errorPage(status.value, details)
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
