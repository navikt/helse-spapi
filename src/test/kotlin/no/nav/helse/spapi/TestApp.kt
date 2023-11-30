package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import java.time.LocalDate


fun main() {
    val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi").start()
    Runtime.getRuntime().addShutdownHook(Thread{ maskinporten.stop() })
    embeddedServer(CIO, port = 8080) {
        testSpapi(maskinporten, object : UtbetaltePerioder {
            override suspend fun hent(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate) = emptyList<UtbetaltPeriode>()
        })
    }.start(wait = true)
}

internal fun Application.testSpapi(maskinporten: Issuer, utbetaltePerioder: UtbetaltePerioder) {
    spapi(
        config = mapOf(
            "MASKINPORTEN_JWKS_URI" to maskinporten.jwksUri(),
            "MASKINPORTEN_ISSUER" to maskinporten.navn(),
            "AUDIENCE" to maskinporten.audience()
        ),
        sporings = object : Sporingslogg() {
            override fun send(logginnslag: JsonNode) {}
        },
        accessToken = object : AccessToken() {
            override suspend fun hentNytt(scope: String) = "1" to 1L
        },
        utbetaltePerioder = utbetaltePerioder,
        personidentifikatorer = object : Personidentifikatorer {
            override suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument) = setOf(personidentifikator)
        }
    )
}