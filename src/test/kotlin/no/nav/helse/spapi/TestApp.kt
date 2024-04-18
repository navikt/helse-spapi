package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import java.time.LocalDate


fun main() {
    val maskinporten = Issuer(navn = "maskinporten", audience = "https://spapi").start()
    Runtime.getRuntime().addShutdownHook(Thread{ maskinporten.stop() })
    embeddedServer(CIO, port = 8085) {
        testSpapi(maskinporten, object : UtbetaltePerioder {
            override suspend fun hent(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate) = emptyList<UtbetaltPeriode>()
        })
        routing {
            get("/token") {
                // http://localhost:8080/token?scope=nav:sykepenger:fellesordningenforafp.read -> Gir token med tilgang for Fellesordningen for AFP
                val claims = call.request.queryParameters.toMap().mapValues { (_, value) -> value.first() }
                call.respondText(maskinporten.accessToken(claims))
            }
        }
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
        accessToken = object : AccessToken {
            override fun get(scope: String) = "1"
        },
        utbetaltePerioder = utbetaltePerioder,
        personidentifikatorer = object : Personidentifikatorer {
            override suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument) = setOf(personidentifikator)
        }
    )
}