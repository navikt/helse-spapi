package no.nav.helse.spapi

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.OK
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class AvtalefestetPensjonTest : SpapiTest() {

    @Test
    fun `sender med minimumSykdomsgrad`() = avtalefestetPensjonTest {
        @Language("JSON")
        val forventetResponse = """
        {
          "saksId": "minimum_80",
          "utbetaltePerioder": [
            {
              "fraOgMedDato": "2018-01-01",
              "tilOgMedDato": "2018-01-31",
              "tags": ["UsikkerSykdomsgrad"]
            }
          ]
        }
        """

        request(minimumSykdomsgrad = 80, saksId = "minimum_80") {
            assertStatus(OK)
            assertResponse(forventetResponse)
        }

        @Language("JSON")
        val forventetResponse2 = """
        {
          "saksId": "minimum_79",
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
        request(minimumSykdomsgrad = 79, saksId = "minimum_79") {
            assertStatus(OK)
            assertResponse(forventetResponse2)
        }
    }

    @Test
    fun `sender ikke med minimumSykdomsgrad`() = avtalefestetPensjonTest {
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

        request(saksId = "Jeg_er_en_Saks-id") {
            assertStatus(OK)
            assertResponse(forventetResponse)
        }
    }

    @Test
    fun `manglende saksId i request`() = avtalefestetPensjonTest(enTilFeldigKonsumentAv = konsumenter - StatensPensjonskasse) { // TODO: SPK Har unntak til 1.feb
        request(saksId = null) {
            assertStatus(BadRequest)
            assertFeilmelding("Mangler feltet 'saksId' i request body.")
        }
    }

    @Test
    fun `manglende saksId i request fra statens pensjonskasse går greit frem til 1 februar 2025`() = avtalefestetPensjonTest(enTilFeldigKonsumentAv = listOf(StatensPensjonskasse)) {
        request(saksId = null) {
            assertStatus(OK)
        }
    }

    @Test
    fun `kan ikke integrere med det delegerte scopet`() = avtalefestetPensjonTest(scope = "nav:sykepenger/delegertavtalefestetpensjon.read") {
        request {
            assertStatus(Forbidden)
        }
    }

    // DrammenKommunalePensjonskasse & ArendalKommunalePensjonskasse testets i AksioTest
    private val konsumenter = AlleKonsumenter.filterIsInstance<AvtalefestetPensjon>()
        .filterNot { it is Nav || it is DrammenKommunalePensjonskasse || it is ArendalKommunalePensjonskasse }

    private fun avtalefestetPensjonTest(
        enTilFeldigKonsumentAv: List<Konsument> = konsumenter,
        scope: String = "nav:sykepenger:avtalefestetpensjon.read",
        block: suspend SpapiTestContext.() -> Unit
    ) = spapiTest(
        organisasjonsnummer = enTilFeldigKonsumentAv.shuffled().first().organisasjonsnummer,
        scope = scope,
        endepunkt = "/avtalefestet-pensjon"
    ) { block() }
}
