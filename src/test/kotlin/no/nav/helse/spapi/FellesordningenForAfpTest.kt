package no.nav.helse.spapi

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
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

internal class FellesordningenForAfpTest : KonsumentTest() {

    @Test
    fun `tilgang for fellesordningen for afp`() = setupSpapi {
        assertEquals(Unauthorized, client.request().status)
        assertEquals(Forbidden, client.request(feilScope()).status)
        assertEquals(Forbidden, client.request(feilIssuer()).status)
        assertEquals(Forbidden, client.request(feilIssuerHeader()).status)
        assertEquals(Forbidden, client.request(feilAudience()).status)
        assertEquals(OK, client.request(riktigToken()).status)
    }

    @Test
    fun `response til fellesordningen for afp`() = setupSpapi {
        @Language("JSON")
        val forventetResponse = """
        {
          "utbetaltePerioder": [
            {
              "fraOgMedDato": "2018-01-01",
              "tilOgMedDato": "2018-01-31"
            },
            {
              "fraOgMedDato": "2019-01-01",
              "tilOgMedDato": "2019-01-31"
            }
          ]
        }
        """
        client.request(riktigToken()).assertResponse(forventetResponse)
    }

    override val scope = "nav:sykepenger:fellesordningenforafp.read"

    override fun utbetaltePerioder() = object : UtbetaltePerioder {
        override suspend fun hent(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate) = listOf(
            UtbetaltPeriode(LocalDate.parse("2018-01-01"), LocalDate.parse("2018-01-31"), "999999999", 100),
            UtbetaltPeriode(LocalDate.parse("2019-01-01"), LocalDate.parse("2019-01-31"), "999999998", 80),
            UtbetaltPeriode(LocalDate.parse("2020-01-01"), LocalDate.parse("2020-01-31"), "999999997", 79),
        )
    }

    private suspend fun HttpClient.request(accessToken: String? = null) = post("/fellesordningen-for-afp") {
        accessToken?.let { header(Authorization, "Bearer $it") }
        @Language("JSON")
        val request = """
        {
          "personidentifikator": "11111111111",
          "organisasjonsnummer": "999999999",
          "fraOgMedDato": "2018-01-01",
          "tilOgMedDato": "2018-01-31",
          "minimumSykdomsgrad": 80
        }
        """
        setBody(request)
    }
}