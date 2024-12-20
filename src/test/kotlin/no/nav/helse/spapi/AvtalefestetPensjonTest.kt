package no.nav.helse.spapi

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class AvtalefestetPensjonTest : KonsumentTest() {

    @Test
    fun `response til avtalefestet pensjon når de utelater minimimSykdomsgrad i requesten`() = setupSpapi {
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
        konsumentMedOptionalSaksId {
            client.request(riktigToken(), minimumSykdomsgrad = null).apply {
                assertEquals(OK, status)
                assertResponse(forventetResponse)
            }
        }
    }

    @Test
    fun `response til avtalefestet pensjon når de inkluderer minimumSykdomsgrad i requesten`() = setupSpapi {
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

        konsumentMedOptionalSaksId {
            client.request(riktigToken(), minimumSykdomsgrad = 80).apply {
                assertEquals(OK, status)
                assertResponse(forventetResponse)
            }
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
        konsumentMedOptionalSaksId {
            client.request(accessToken = riktigToken(), minimumSykdomsgrad = 79).apply {
                assertEquals(OK, status)
                assertResponse(forventetResponse2)
            }
        }
    }

    @Test
    fun `response til avtalefestet pensjon når de inkluderer saksId i requesten`() = setupSpapi {
        @Language("JSON")
        val forventetResponse = """
        {
          "saksId": "Jeg_er_en_Saks-id",
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

        konsumentMedOptionalSaksId {
            client.request(riktigToken(), minimumSykdomsgrad = null, saksId = "Jeg_er_en_Saks-id").apply {
                assertEquals(OK, status)
                assertResponse(forventetResponse)
            }
        }

        konsumentMedRequiredSaksId {
            client.request(riktigToken(), minimumSykdomsgrad = null, saksId = "Jeg_er_en_Saks-id").apply {
                assertEquals(OK, status)
                assertResponse(forventetResponse)
            }
        }
    }

    @Test
    fun `manglende saksId for de som må sende det gir 400`() = setupSpapi {
        konsumentMedRequiredSaksId { client.request(riktigToken(), saksId = null).let {
            assertEquals(BadRequest, it.status)
            assertTrue(it.bodyAsText().contains("Mangler feltet 'saksId' i request body."))
        }}
        // Mens for de med optional går det greit
        konsumentMedOptionalSaksId { assertEquals(OK, client.request(riktigToken(), saksId = null).status) }
    }

    override val scope = "nav:sykepenger:avtalefestetpensjon.read"

    private val optionalSaksid = setOf(StatensPensjonskasse, KommunalLandspensjonskasse)
    private val requiredSaksId = AlleKonsumenter - optionalSaksid - FellesordningenForAfp - ArendalKommunalePensjonskasse - DrammenKommunalePensjonskasse

    private var aktivtOrganisasjonsnummer: Organisasjonsnummer? = null
    override val organisasjonsnummer get() = checkNotNull(aktivtOrganisasjonsnummer)

    private inline fun konsumentMedOptionalSaksId(block: () -> Unit) {
        aktivtOrganisasjonsnummer = optionalSaksid.random().organisasjonsnummer
        block()
        aktivtOrganisasjonsnummer = null
    }

    private inline fun konsumentMedRequiredSaksId(block: () -> Unit) {
        aktivtOrganisasjonsnummer = requiredSaksId.random().organisasjonsnummer
        block()
        aktivtOrganisasjonsnummer = null
    }

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

    private suspend fun HttpClient.request(accessToken: String? = null, minimumSykdomsgrad: Int? = 80, tomKey: String = "tilOgMedDato", tomValue: String = "2018-01-31", saksId: String? = null) = post("/avtalefestet-pensjon",) {
        accessToken?.let { header(Authorization, "Bearer $it") }
        @Language("JSON")
        val request = """
        {
          "personidentifikator": "11111111111",
          "organisasjonsnummer": "999999999",
          "fraOgMedDato": "2018-01-01",
          "$tomKey": "$tomValue"
          ${if (minimumSykdomsgrad != null) ",\"minimumSykdomsgrad\": $minimumSykdomsgrad" else ""}
          ${if (saksId != null) ",\"saksId\": \"$saksId\"" else ""}
        }
        """
        setBody(request)
    }
}