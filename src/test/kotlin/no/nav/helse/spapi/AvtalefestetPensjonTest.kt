package no.nav.helse.spapi

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.*
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class AvtalefestetPensjonTest : KonsumentTest() {

    @Test
    fun `tilgang for statens pensjonskasse`() = setupSpapi {
        assertEquals(Unauthorized, client.request().status)
        assertEquals(Forbidden, client.request(feilScope()).status)
        assertEquals(Forbidden, client.request(valgfrittScope("nav:sykepenger:fellesordningenforafp.read")).status)
        assertEquals(Forbidden, client.request(valgfrittScope("nav:sykepenger:storebrandpensjonstjenester.read")).status)
        assertEquals(Forbidden, client.request(feilIssuer()).status)
        assertEquals(Forbidden, client.request(feilIssuerHeader()).status)
        assertEquals(Forbidden, client.request(feilAudience()).status)
        assertEquals(OK, client.request(riktigToken()).status)
    }

    @Test
    fun `response til statens pensjonskasse for statens pensjonskasse når de utelater minimimSykdomsgrad i requesten`() = setupSpapi {
        @Language("JSON")
        val forventetResponse = """
        {
          "utbetaltePerioder": [
            {
              "fraOgMedDato": "2018-01-01",
              "tilOgMedDato": "2018-01-31",
              "tags": [
                "UsikkerSykdomsgrad"
              ],
              "sykdomsgrad": 100
            },
            {
              "fraOgMedDato": "2020-01-01",
              "tilOgMedDato": "2020-01-31",
              "tags": [],
              "sykdomsgrad": 79
            }
          ]
        }
        """
        client.request(riktigToken(), minimumSykdomsgrad = null).apply {
            assertEquals(OK, status)
            assertResponse(forventetResponse)
        }
    }

    @Test
    fun `response til fellesordningen for afp når de inkluderer minimumSykdomsgrad i requesten`() = setupSpapi {
        @Language("JSON")
        val forventetResponse = """
        {
          "utbetaltePerioder": [
            {
              "fraOgMedDato": "2018-01-01",
              "tilOgMedDato": "2018-01-31",
              "tags": ["UsikkerSykdomsgrad"]
            }
          ]
        }
        """
        client.request(riktigToken(), minimumSykdomsgrad = 80).apply {
            assertEquals(OK, status)
            assertResponse(forventetResponse)
        }

        @Language("JSON")
        val forventetResponse2 = """
        {
          "utbetaltePerioder": [
            {
              "fraOgMedDato": "2018-01-01",
              "tilOgMedDato": "2018-01-31",
              "tags": ["UsikkerSykdomsgrad"]
            },
            {
              "fraOgMedDato": "2020-01-01",
              "tilOgMedDato": "2020-01-31",
              "tags": []
            }
          ]
        }
        """
        client.request(accessToken = riktigToken(), minimumSykdomsgrad = 79).apply {
            assertEquals(OK, status)
            assertResponse(forventetResponse2)
        }
    }

    @Test
    fun `manglende input gir 400`() =  setupSpapi {
        assertEquals(BadRequest, client.request(riktigToken(), tomKey = "tomOgMedDato").status)
    }

    @Test
    fun `ugyldig input gir 400`() =  setupSpapi {
        assertEquals(BadRequest, client.request(riktigToken(), tomValue = "kittycat").status)
    }


    override val scope = "nav:sykepenger:avtalefestetpensjon.read"
    override val organisasjonsnummer = (Konsument.AlleKonsumenter - FellesordningenForAfp).random().organisasjonsnummer

    override fun utbetaltePerioder() = object : UtbetaltePerioder {
        override suspend fun hent(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate) = listOf(
            UtbetaltPeriode(LocalDate.parse("2018-01-01"), LocalDate.parse("2018-01-31"), Organisasjonsnummer("999999999"), 100, setOf("UsikkerSykdomsgrad")),
            UtbetaltPeriode(LocalDate.parse("2019-01-01"), LocalDate.parse("2019-01-31"), Organisasjonsnummer("999999998"), 80, setOf()),
            UtbetaltPeriode(LocalDate.parse("2020-01-01"), LocalDate.parse("2020-01-31"), Organisasjonsnummer("999999999"), 79, setOf())
        ).also {
            assertEquals(LocalDate.parse("2018-01-01"), fom)
            assertEquals(LocalDate.parse("2018-01-31"), tom)
            assertEquals(setOf(Personidentifikator("11111111111")), personidentifikatorer)
        }
    }

    private suspend fun HttpClient.request(accessToken: String? = null, minimumSykdomsgrad: Int? = 80, tomKey: String = "tilOgMedDato", tomValue: String = "2018-01-31") = post("/avtalefestet-pensjon") {
        accessToken?.let { header(Authorization, "Bearer $it") }
        @Language("JSON")
        val request = """
        {
          "personidentifikator": "11111111111",
          "organisasjonsnummer": "999999999",
          "fraOgMedDato": "2018-01-01",
          "$tomKey": "$tomValue"
          ${if (minimumSykdomsgrad != null) ",\"minimumSykdomsgrad\": $minimumSykdomsgrad" else ""}
        }
        """
        setBody(request)
    }
}