package no.nav.helse.spapi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
internal class MaskinportenTilgangTest {

    @Test
    fun `tilgang for fellesordningen for afp`() = setupSpapi {
        assertEquals(Unauthorized, client.post("/fellesordningen-for-afp").status)
        // Burde ikke disse gitt Forbidden? 🤔
        assertEquals(Unauthorized, client.post("/fellesordningen-for-afp") { header(Authorization, "Bearer ${feilScope()}") }.status)
        assertEquals(Unauthorized, client.post("/fellesordningen-for-afp") { header(Authorization, "Bearer ${feilIssuer()}") }.status)
        assertEquals(Unauthorized, client.post("/fellesordningen-for-afp") { header(Authorization, "Bearer ${feilAudience()}") }.status)
        assertEquals(OK, client.post("/fellesordningen-for-afp") { header(Authorization, "Bearer ${riktigToken()}") }.status)
    }

    private fun setupSpapi(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application { spapi(mapOf(
                "MASKINPORTEN_JWKS_URI" to maskinporten.jwksUri(),
                "MASKINPORTEN_ISSUER" to maskinporten.navn(),
                "AUDIENCE" to maskinporten.audience()
            )) }
            block()
        }
    }

    private val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi")
    private val riktigScope = "nav:sykepenger:fellesordningenforafp.read"
    private fun riktigToken() = maskinporten.accessToken(mapOf("scope" to riktigScope))
    private fun feilScope() = maskinporten.accessToken(mapOf("scope" to "nav:sykepenger:fellesordningenforafp.write"))
    private fun feilIssuer() = maskinporten.accessToken(mapOf("scope" to riktigScope, "iss" to "feil-issuer"))
    private fun feilAudience() = maskinporten.accessToken(mapOf("scope" to riktigScope, "aud" to "feil-audience"))

    @BeforeAll
    fun start() = maskinporten.start()

    @AfterAll
    fun stop() = maskinporten.stop()
}