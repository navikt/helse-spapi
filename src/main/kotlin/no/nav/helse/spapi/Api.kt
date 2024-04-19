package no.nav.helse.spapi

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.slf4j.LoggerFactory
import java.util.*

internal class Api(vararg konsumenter: Konsument, private val id: String, private val scope: String) {
    internal constructor(konsument: Konsument): this(konsument, id = konsument.id, scope = konsument.scope)

    private val konsumenter = konsumenter.toSet().also {
        check(konsumenter.isNotEmpty()) { "Må sette minst en konsument!" }
    }

    internal fun registerAuthentication(authenticationConfig: AuthenticationConfig, maskinportenJwkProvider: JwkProvider, maskinportenIssuer: String, audience: String) {
        sikkerlogg.info("Registrerer Authentication for $konsumenter på id $id")
        authenticationConfig.jwt(id) {
            verifier(maskinportenJwkProvider, maskinportenIssuer) {
                withAudience(audience)
                withClaim("scope", scope)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
            challenge { _, _ -> call.respondChallenge() }
        }
    }

    internal fun registerApi(routing: Routing, utbetaltePerioder: UtbetaltePerioder, personidentifikatorer: Personidentifikatorer, sporings: Sporingslogg) {
        sikkerlogg.info("Registrerer API for $konsumenter på endepunkt /$id")
        routing.authenticate(id) {
            post("/$id") {
                val requestBody = call.requestBody()
                val konsument = call.konsument(konsumenter)
                sikkerlogg.info("Mottok request fra ${konsument}:\n\t$requestBody")

                val request = konsument.request(requestBody)

                val perioder = utbetaltePerioder.hent(
                    personidentifikatorer = personidentifikatorer.hentAlle(request.personidentifikator, konsument),
                    fom = request.fom,
                    tom = request.tom
                )

                val response = response(perioder, request)

                sporings.logg(
                    person = request.personidentifikator,
                    konsument = konsument,
                    leverteData = response
                )

                call.respondText(response, ContentType.Application.Json)
            }
        }
    }

    private fun response(utbetaltePerioder: List<UtbetaltPeriode>, request: KonsumentRequest): String {
        val utlevertePerioder = request.filtrer(utbetaltePerioder).map { objectMapper.readTree(request.json(it)) }
        return objectMapper.createObjectNode().apply {
            putArray("utbetaltePerioder").addAll(utlevertePerioder)
        }.toString()
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
        private suspend fun ApplicationCall.requestBody() = try { objectMapper.readTree(receiveText()) } catch (throwable: Throwable) {
            objectMapper.createObjectNode()
        }
        private suspend fun ApplicationCall.respondChallenge() {
            try {
                val jwt = request.header(HttpHeaders.Authorization)
                    ?.substringAfter("Bearer ")
                    ?.split(".")
                    ?.takeIf { it.size == 3 }
                    ?: return respond(HttpStatusCode.Unauthorized, "Bearer token må settes i Authorization header for å hente data fra Spaπ!")
                sikkerlogg.error("Mottok request med access token som ikke har tilgang til Spaπ sitt endepunkt ${request.path()}!\n\tJWT Headers: ${String(Base64.getUrlDecoder().decode(jwt[0]))}\n\tJWT Payload: ${String(Base64.getUrlDecoder().decode(jwt[1]))}")
                respond(HttpStatusCode.Forbidden, "Bearer token som er brukt har ikke rett tilgang til å hente data fra Spaπ! Ta kontakt med NAV.")
            } catch (throwable: Throwable) {
                respond(HttpStatusCode.Unauthorized, "Bearer token må settes i Authorization header for å hente data fra Spaπ!")
            }
        }
        private fun ApplicationCall.konsument(konsumenter: Set<Konsument>): Konsument {
            val organisasjonsnummer = principal<JWTPrincipal>()?.payload?.getClaim("consumer")?.asMap()?.get("ID")?.toString()?.substringAfter(":") ?: error("Klarte ikke utlede konsuement fra token")
            return konsumenter.singleOrNull { it.organisasjonsnummer.toString() == organisasjonsnummer } ?: error("Konsument med orgnr $organisasjonsnummer er ikke registrert.")
        }
    }
}