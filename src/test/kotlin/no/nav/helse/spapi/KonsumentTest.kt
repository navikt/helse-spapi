package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.naisful.test.naisfulTestApp
import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.skyscreamer.jsonassert.JSONAssert

@TestInstance(PER_CLASS)
internal abstract class KonsumentTest{

    @BeforeAll
    fun start(){
        maskinporten.start()
    }

    @AfterAll
    fun stop() = maskinporten.stop()


    protected fun setupSpapi(utbetaltePerioder: UtbetaltePerioder, scope: String, organisasjonsnummer: Organisasjonsnummer, block: suspend SpapiTestContext.() -> Unit) {
        naisfulTestApp(
            testApplicationModule = { spapi()},
            objectMapper = jacksonObjectMapper(),
            meterRegistry = ,
            testblokk = {
                block(SpapiTestContext(maskinporten, feilIssuer, client, organisasjonsnummer, scope))}
        )
    }

    private val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi")
    private val feilIssuer = Issuer(navn = "ikke-maskinporten", audience = "https://spapi")

}

internal data class SpapiTestContext(val maskinporten: Issuer, val feilIssuer: Issuer, val client: HttpClient, val organisasjonsnummer: Organisasjonsnummer, val scope: String) {
    fun riktigToken() = maskinporten.accessToken(mapOf("scope" to scope), organisasjonsnummer)
    fun feilOrganisasjonsnummer(organisasjonsnummer: Organisasjonsnummer) = maskinporten.accessToken(mapOf("scope" to scope), organisasjonsnummer)
    fun feilScope() = maskinporten.accessToken(mapOf("scope" to "$scope-med-feil"), organisasjonsnummer)
    fun valgfrittScope(valgfrittScope: String) = maskinporten.accessToken(mapOf("scope" to valgfrittScope), organisasjonsnummer)
    fun feilIssuerHeader() = maskinporten.accessToken(mapOf("scope" to scope, "iss" to "feil-issuer"), organisasjonsnummer)
    fun feilIssuer() = feilIssuer.accessToken(mapOf("scope" to scope), organisasjonsnummer)
    fun feilAudience() = maskinporten.accessToken(mapOf("scope" to scope, "aud" to "feil-audience"), organisasjonsnummer)

    suspend fun HttpResponse.assertResponse(forventet: String) = JSONAssert.assertEquals(forventet, bodyAsText(), true)
}
