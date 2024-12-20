package no.nav.helse.spapi

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

    abstract val scope: String
    abstract val organisasjonsnummer: Organisasjonsnummer
    abstract fun utbetaltePerioder(): UtbetaltePerioder

    @BeforeAll
    fun start(){
        maskinporten.start()
    }

    @AfterAll
    fun stop() = maskinporten.stop()


    protected fun setupSpapi(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application { testSpapi(maskinporten, utbetaltePerioder()) }
            block()
        }
    }

    private val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi")

    protected fun riktigToken() = maskinporten.accessToken(mapOf("scope" to scope), organisasjonsnummer)

    protected suspend fun HttpResponse.assertResponse(forventet: String) = JSONAssert.assertEquals(forventet, bodyAsText(), true)
}