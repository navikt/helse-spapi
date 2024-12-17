package no.nav.helse.spapi

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class AksioTest : SpapiTest() {

    @Test
    fun `Aksio integrerer på vegne av Drammen kommune`() = aksioTest(Drammen) {
        request {
            assertStatus(OK)
            @Language("JSON")
            val forventet = """
                {"saksId":"jeg-er-en-saksId","utbetaltePerioder":[{"fraOgMedDato":"2018-01-01","tilOgMedDato":"2018-01-31","tags":["UsikkerSykdomsgrad"],"sykdomsgrad":100},{"fraOgMedDato":"2020-01-01","tilOgMedDato":"2020-01-31","tags":[],"sykdomsgrad":79}]}
            """
            assertResponse(forventet)
        }
    }

    @Test
    fun `Aksio integrerer på vegne av Arendal kommune`() = aksioTest(Arendal) {
        request {
            assertStatus(OK)
            @Language("JSON")
            val forventet = """
                {"saksId":"jeg-er-en-saksId","utbetaltePerioder":[{"fraOgMedDato":"2018-01-01","tilOgMedDato":"2018-01-31","tags":["UsikkerSykdomsgrad"],"sykdomsgrad":100},{"fraOgMedDato":"2020-01-01","tilOgMedDato":"2020-01-31","tags":[],"sykdomsgrad":79}]}
            """
            assertResponse(forventet)
        }
    }

    @Test
    fun `Feil integrator forsøker å integrere for Arendal kommune`() = aksioTest(Arendal, integrator = Drammen) {
        request {
            assertStatus(InternalServerError)
        }
    }

    @Test
    fun `Arendal kommune forsøker å integrere selv`() = aksioTest(Arendal, integrator = null) {
        request {
            assertStatus(InternalServerError)
        }
    }

    @Test
    fun `Aksio integrerer på vegne av Drammen kommune med feil scope`() = aksioTest(Drammen, scope = "åpenbart-feil-scope") {
        request {
            assertStatus(Forbidden)
        }
    }

    @Test
    fun `Aksio integrerer på vegne av Drammen kommune mot feil endepunkt`() = aksioTest(Drammen, endepunkt = "/fellesordningen-for-afp") {
        request {
            assertStatus(Forbidden)
        }
    }

    @Test
    fun `Aksio integrerer på vegne av Drammen kommune uten saksId`() = aksioTest(Drammen) {
        request(saksId = null) {
            assertStatus(BadRequest)
        }
    }

    private val Drammen = Organisasjonsnummer("980650383")
    private val Aksio = Organisasjonsnummer("927613298")
    private val Arendal = Organisasjonsnummer("940380014")

    private fun aksioTest(
        organisasjonsnummer: Organisasjonsnummer,
        integrator: Organisasjonsnummer? = Aksio,
        scope: String = "nav:sykepenger/delegertavtalefestetpensjon.read",
        endepunkt: String = "/avtalefestet-pensjon",
        block: suspend SpapiTestContext.() -> Unit
    ) = spapiTest(
        organisasjonsnummer = organisasjonsnummer,
        scope = scope,
        integrator = integrator,
        endepunkt = endepunkt
    ) { block() }
}