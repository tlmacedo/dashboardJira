package br.sidia.painel

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun healthRetornaOk() = testApplication {
        application { module(Settings.fromEnv(emptyMap())) }
        val r = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"ok":true}""", r.bodyAsText())
    }
}
