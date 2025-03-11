package no.nav.helse.spapi

import com.github.navikt.tbd_libs.naisful.test.plainTestApp
import com.github.navikt.tbd_libs.signed_jwt_issuer_test.Issuer
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.time.LocalDate

@TestInstance(PER_CLASS)
internal abstract class SpapiTest{

    private val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi")

    @BeforeAll
    fun start(){
        maskinporten.start()
    }

    @AfterAll
    fun stop() {
        maskinporten.stop()
    }

    private val defaultUtbetaltePerioder = object : UtbetaltePerioder {
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

    protected fun spapiTest(
        organisasjonsnummer: Organisasjonsnummer,
        scope: String,
        endepunkt: String,
        utbetaltePerioder: UtbetaltePerioder = defaultUtbetaltePerioder,
        integrator: Organisasjonsnummer? = null,
        block: suspend SpapiTestContext.() -> Unit
    ) = plainTestApp(
        isreadyEndpoint = "/internal/isready",
        testApplicationModule = {
            testSpapi(maskinporten, utbetaltePerioder)
        },
        testblokk = {
            block(
                SpapiTestContext(
                    maskinporten = maskinporten,
                    client = client,
                    konsument = organisasjonsnummer,
                    scope = scope,
                    endepunkt = endepunkt,
                    integrator = integrator
                )
            )
        })


}
