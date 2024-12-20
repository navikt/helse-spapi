package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UgyldigInputTest: SpapiTest() {

    @Test
    fun `manglende input gir 400`() = fellesordningenForAfpTest {
        @Language("JSON")
        val body = """
        {
          "personidentifikator": "11111111111",
          "organisasjonsnummer": "999999999",
          "fraOgMedDato": "2018-01-01",
          "tomOgMedDato": "2018-01-31"
        }
        """
        requestRaw(body = body) {
            assertStatus(BadRequest)
            assertFeilmelding("Mangler feltet 'tilOgMedDato' i request body.")
        }
    }

    @Test
    fun `ugyldig input gir 400`() =  fellesordningenForAfpTest {
        @Language("JSON")
        val body = """
        {
          "personidentifikator": "11111111111",
          "organisasjonsnummer": "999999999",
          "fraOgMedDato": "2018-01-01",
          "tilOgMedDato": "kittycat"
        }
        """
        requestRaw(body = body) {
            assertStatus(BadRequest)
            assertFeilmelding("Ugyldig verdi i feltet 'tilOgMedDato' i request body. (var kittycat)")
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