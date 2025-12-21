package de.ard.audiothek

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `root responds with status text`() = testApplication {
        application {
            module()
        }

        val response = client.get("/")
        assertEquals("ARD Audiothek RSS Adapter is running.", response.bodyAsText())
    }
}
