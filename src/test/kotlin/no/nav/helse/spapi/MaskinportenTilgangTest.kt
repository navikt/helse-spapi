package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.Forbidden
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
        assertEquals(Unauthorized, client.fellesordningenForAfpRequest().status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilScope()).status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilIssuer()).status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilIssuerHeader()).status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilAudience()).status)
        assertEquals(OK, client.fellesordningenForAfpRequest(riktigToken()).status)
    }

    private fun setupSpapi(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application { spapi(
                config = mapOf(
                    "MASKINPORTEN_JWKS_URI" to maskinporten.jwksUri(),
                    "MASKINPORTEN_ISSUER" to maskinporten.navn(),
                    "AUDIENCE" to maskinporten.audience()
                ),
                sporings = object : Sporingslogg() {
                    override fun send(logginnslag: JsonNode) {}
                },
                accessToken = object : AccessToken {
                    override suspend fun get(scope: String) = "accessToken"
                }
            )}
            block()
        }
    }

    private suspend fun HttpClient.fellesordningenForAfpRequest(accessToken: String? = null) = post("/fellesordningen-for-afp") {
        accessToken?.let { header(Authorization, "Bearer $it") }
        setBody("""{"personidentifikator": "11111111111"}""")
    }

    private val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi")
    private val feilIssuer = Issuer(navn = "ikke-maskinporten", audience = "https://spapi")
    private val riktigScope = "nav:sykepenger:fellesordningenforafp.read"

    private fun riktigToken() = maskinporten.accessToken(mapOf("scope" to riktigScope))
    private fun feilScope() = maskinporten.accessToken(mapOf("scope" to "nav:sykepenger:fellesordningenforafp.write"))
    private fun feilIssuerHeader() = maskinporten.accessToken(mapOf("scope" to riktigScope, "iss" to "feil-issuer"))
    private fun feilIssuer() = feilIssuer.accessToken(mapOf("scope" to riktigScope))
    private fun feilAudience() = maskinporten.accessToken(mapOf("scope" to riktigScope, "aud" to "feil-audience"))

    @BeforeAll
    fun start() = maskinporten.start()

    @AfterAll
    fun stop() = maskinporten.stop()
}