package no.nav.helse.spapi

import io.ktor.http.HttpStatusCode.Companion.OK
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class FellesordningenForAfpTest : SpapiTest() {

    @Test
    fun `response til fellesordningen for afp når de utelater minimimSykdomsgrad i requesten`() = fellesordningenForAfpTest {
        @Language("JSON")
        val forventetResponse = """
        {
          "utbetaltePerioder": [
            {
              "fraOgMedDato": "2018-01-01",
              "tilOgMedDato": "2018-01-31",
              "sykdomsgrad": 100,
              "tags": ["UsikkerSykdomsgrad"]
            },
            {
              "fraOgMedDato": "2020-01-01",
              "tilOgMedDato": "2020-01-31",
              "sykdomsgrad": 79,
              "tags": []
            }
          ]
        }
        """
        request {
            assertStatus(OK)
            assertResponse(forventetResponse)
        }
    }

    @Test
    fun `response til fellesordningen for afp når de inkluderer minimumSykdomsgrad i requesten`() = fellesordningenForAfpTest {
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
        request(minimumSykdomsgrad = 80) {
            assertStatus(OK)
            assertResponse(forventetResponse)
        }
    }

    private fun fellesordningenForAfpTest(
        block: suspend SpapiTestContext.() -> Unit
    ) = spapiTest(
        organisasjonsnummer = Organisasjonsnummer("987414502"),
        scope = "nav:sykepenger:fellesordningenforafp.read",
        endepunkt = "/fellesordningen-for-afp"
    ) { block() }
}