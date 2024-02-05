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
import io.ktor.util.pipeline.*
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

internal open class Konsument(
    private val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    private val id: String,
    private val scope: String,
    internal val behandlingsnummer: String,
    internal val behandlingsgrunnlag: Behandlingsgrunnlag
) {
    override fun toString() = navn

    internal fun setupAuthentication(authenticationConfig: AuthenticationConfig, maskinportenJwkProvider: JwkProvider, maskinportenIssuer: String, audience: String) {
        authenticationConfig.jwt(id) {
            verifier(maskinportenJwkProvider, maskinportenIssuer) {
                withAudience(audience)
                withClaim("scope", scope)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
            challenge { _, _ -> call.respondChallenge() }
        }
    }

    internal fun setupApi(routing: Routing, block: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit) {
        routing.authenticate(id) {
            post("/$id") {
                block(this)
            }
        }
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
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
    }
}

internal object FellesordningenForAfp: Konsument(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    id = "fellesordningen-for-afp",
    scope = "nav:sykepenger:fellesordningenforafp.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
) {
    private val objectMapper = jacksonObjectMapper()
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    internal data class Request(val personidentifikator: Personidentifikator, val fom: LocalDate, val tom: LocalDate, val organisasjonsnummer: Organisasjonsnummer, val minimumSykdomsgrad: Int?)

    internal suspend fun request(call: ApplicationCall): Request {
        val requestBody = call.requestBody()
        val personidentifikator = requestBody.required("personidentifikator") { Personidentifikator(it.asText()) }
        val organisasjonsnummer = requestBody.required("organisasjonsnummer") { Organisasjonsnummer(it.asText()) }
        val fom = requestBody.required("fraOgMedDato") { LocalDate.parse(it.asText()) }
        val tom = requestBody.required("tilOgMedDato") {
            LocalDate.parse(it.asText()).also { tom -> check(fom <= tom) { "Ugyldig periode $fom til $tom" } }
        }
        val minimumSykdomsgrad = requestBody.optional("minimumSykdomsgrad") {
            it.asInt().also { minimumSykdomsgrad -> check(minimumSykdomsgrad in 1..100) { "Må være mellom 1 og 100" } }
        }
        return Request(personidentifikator, fom, tom, organisasjonsnummer, minimumSykdomsgrad).also {
            sikkerlogg.info("Mottok request fra $this:\n\t$it")
        }
    }

    internal fun response(utbetaltePerioder: List<UtbetaltPeriode>, request: Request): String {
        val utlevertePerioder = utbetaltePerioder
            .filter { it.organisasjonsnummer == request.organisasjonsnummer }
            .filter { it.grad >= (request.minimumSykdomsgrad ?: 0) }
            .map { objectMapper.createObjectNode()
            .apply {
                put("fraOgMedDato", "${it.fom}")
                put("tilOgMedDato", "${it.tom}")
                .apply { putArray("tags").let { tags -> it.tags.forEach(tags::add) } }
                if (request.minimumSykdomsgrad == null) put("sykdomsgrad", it.grad)
        }}
        return objectMapper.createObjectNode().apply {
            putArray("utbetaltePerioder").addAll(utlevertePerioder)
        }.toString()
    }
}

