package de.ard.audiothek.ard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ShowPageClientExternalTest {
    private lateinit var httpClient: HttpClient
    private val parser = ArdShowPageParser(jacksonObjectMapper())

    @BeforeTest
    fun setUpClient() {
        httpClient = HttpClient(CIO) {
            expectSuccess = false
        }
    }

    @AfterTest
    fun tearDownClient() {
        httpClient.close()
    }

    @Disabled("External test that depends on ARD Audiothek availability")
    @Test
    fun `fetchShow can load Kein Mucks production from ARD`() = runBlocking {
        val client = ShowPageClient(httpClient, parser)
        val feedUrl = "https://www.ardaudiothek.de/sendung/kein-mucks-der-krimi-podcast-mit-bastian-pastewka/urn:ard:show:e01e22ff9344b2a4/"

        val show = client.fetchShow(feedUrl)

        assertTrue(show.title.contains("Kein Mucks"), "Expected Kein Mucks show title but was '${show.title}'")
        assertTrue(show.episodes.isNotEmpty(), "Expected Kein Mucks show to expose episodes")
    }
}
