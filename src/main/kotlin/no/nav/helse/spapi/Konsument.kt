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
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

internal interface KonsumentRequest {
    val fom: LocalDate
    val tom: LocalDate
    val personidentifikator: Personidentifikator
}

internal typealias Konsument = EnKonsument<out KonsumentRequest>

internal abstract class EnKonsument<Req: KonsumentRequest>(
    private val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    private val id: String,
    private val scope: String,
    internal val behandlingsnummer: String,
    internal val behandlingsgrunnlag: Behandlingsgrunnlag
) {
    override fun toString() = navn

    internal fun registerAuthentication(authenticationConfig: AuthenticationConfig, maskinportenJwkProvider: JwkProvider, maskinportenIssuer: String, audience: String) {
        sikkerlogg.info("Registrerer Authentication for $this")
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
        sikkerlogg.info("Registrerer API for $this")
        routing.authenticate(id) {
            post("/$id") {
                val request = request(call)

                sikkerlogg.info("Mottok request fra ${this@EnKonsument}:\n\t$it")

                val perioder = utbetaltePerioder.hent(
                    personidentifikatorer = personidentifikatorer.hentAlle(request.personidentifikator, this@EnKonsument),
                    fom = request.fom,
                    tom = request.tom
                )

                val response = response(perioder, request)

                sporings.logg(
                    person = request.personidentifikator,
                    konsument = this@EnKonsument,
                    leverteData = response
                )

                call.respondText(response, ContentType.Application.Json)
            }
        }
    }

    abstract suspend fun request(call: ApplicationCall): Req

    abstract suspend fun response(utbetaltePerioder: List<UtbetaltPeriode>, request: Req): String

    protected companion object {
        protected val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        @JvmStatic
        protected val objectMapper = jacksonObjectMapper()

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

internal object FellesordningenForAfp: EnKonsument<FellesordningenForAfp.Request>(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    id = "fellesordningen-for-afp",
    scope = "nav:sykepenger:fellesordningenforafp.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
) {
    internal data class Request(override val personidentifikator: Personidentifikator, override val fom: LocalDate, override val tom: LocalDate, val organisasjonsnummer: Organisasjonsnummer, val minimumSykdomsgrad: Int?): KonsumentRequest

    override suspend fun request(call: ApplicationCall): Request {
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
        return Request(personidentifikator, fom, tom, organisasjonsnummer, minimumSykdomsgrad)
    }

    override suspend fun response(utbetaltePerioder: List<UtbetaltPeriode>, request: Request): String {
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