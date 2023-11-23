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
import no.nav.helse.spapi.Spøkelse.Periode
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate

@TestInstance(PER_CLASS)
internal class MaskinportenTilgangTest {

    @Test
    fun `tilgang for fellesordningen for afp`() = setupSpapi {
        assertEquals(Unauthorized, client.fellesordningenForAfpRequest().status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilScope()).status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilIssuer()).status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilIssuerHeader()).status)
        assertEquals(Forbidden, client.fellesordningenForAfpRequest(feilAudience()).status)
        @Language("JSON")
        val forventetResponse = """
        {
          "utbetaltePerioder": [{ "fom": "2018-01-01", "tom": "2018-01-31"}, { "fom": "2019-01-01", "tom": "2019-01-31"}]
        }
        """
        val response = client.fellesordningenForAfpRequest(riktigToken())
        assertEquals(OK, response.status)
        JSONAssert.assertEquals(forventetResponse, response.bodyAsText(), true)
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
                accessToken = object : AccessToken() {
                    override suspend fun hentNytt(scope: String) = "1" to 1L
                },
                spøkelse = object : Spøkelse {
                    override suspend fun utbetaltePerioder(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate) = listOf(
                        Periode(LocalDate.parse("2018-01-01"), LocalDate.parse("2018-01-31"), "999999999", 100),
                        Periode(LocalDate.parse("2019-01-01"), LocalDate.parse("2019-01-31"), "999999998", 80),
                        Periode(LocalDate.parse("2020-01-01"), LocalDate.parse("2020-01-31"), "999999997", 79),
                    )
                },
                personidentifikatorer = object : Personidentifikatorer {
                    override suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument) = setOf(personidentifikator)
                }
            )}
            block()
        }
    }

    private suspend fun HttpClient.fellesordningenForAfpRequest(accessToken: String? = null) = post("/fellesordningen-for-afp") {
        accessToken?.let { header(Authorization, "Bearer $it") }
        @Language("JSON")
        val body = """
        {
          "personidentifikator": "11111111111",
          "organisasjonsnummer": "999999999",
          "fom": "2018-01-01",
          "tom": "2018-01-31"
        }
        """
        setBody(body)
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