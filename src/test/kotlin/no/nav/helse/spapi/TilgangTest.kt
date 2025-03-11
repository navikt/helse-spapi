package no.nav.helse.spapi

import com.github.navikt.tbd_libs.signed_jwt_issuer_test.Issuer
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import org.junit.jupiter.api.Test

internal class TilgangTest: SpapiTest() {

    @Test
    fun `manglende access token`() = fellesordningenForAfpTest {
        request(accessToken = null) { assertStatus(Unauthorized) }
    }

    @Test
    fun `access token med feil scope`() = fellesordningenForAfpTest {
        request(accessToken = accessTokenFeilScope()) { assertStatus(Forbidden) }
    }

    @Test
    fun `access token med feil audience`() = fellesordningenForAfpTest {
        request(accessToken = accessTokenFeilAudience()) { assertStatus(Forbidden) }
    }

    @Test
    fun `access token med feil konsument`() = fellesordningenForAfpTest {
        request(accessToken = accessTokenFeilKonsument()) { assertStatus(InternalServerError) }
    }

    @Test
    fun `access token fra feil issuer`() = fellesordningenForAfpTest {
        request(accessToken = accessTokenFeilIssuer()) { assertStatus(Forbidden) }
    }

    private val feilIssuer = Issuer(navn = "ikke-maskinporten", audience = "https://spapi")
    private val riktigKonsument = Organisasjonsnummer("987414502")
    private val riktigScope = "nav:sykepenger:fellesordningenforafp.read"

    private fun SpapiTestContext.accessTokenFeilKonsument() = maskinporten.maskinportenAccessToken(konsument = Organisasjonsnummer("982583462") , claims = mapOf("scope" to riktigScope))
    private fun SpapiTestContext.accessTokenFeilScope() = maskinporten.maskinportenAccessToken(konsument = riktigKonsument, claims = mapOf("scope" to "feil-scope"))
    private fun SpapiTestContext.accessTokenFeilAudience() = maskinporten.maskinportenAccessToken(konsument = riktigKonsument, claims = mapOf("scope" to riktigScope, "aud" to "feil-audience"))
    private fun accessTokenFeilIssuer() = feilIssuer.maskinportenAccessToken(konsument = riktigKonsument, claims = mapOf("scope" to riktigScope))

    private fun fellesordningenForAfpTest(
        organisasjonsnummer: Organisasjonsnummer = riktigKonsument,
        scope: String = riktigScope,
        endepunkt: String = "/fellesordningen-for-afp",
        block: suspend SpapiTestContext.() -> Unit
    ) = spapiTest(
        organisasjonsnummer = organisasjonsnummer,
        scope = scope,
        endepunkt = endepunkt
    ) { block() }
}
