package no.nav.helse.spapi

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
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

internal class Api(internal val id: String, scope: String, internal val navn: String, internal val konsumenter: Set<Konsument>) {
    private val vanligScope = "nav:sykepenger:$scope"
    private val delegertScope = "nav:sykepenger/delegert$scope".takeIf { konsumenter.any { it.integrator != null } }

    internal val scopes = setOfNotNull(vanligScope, delegertScope)

    init {
        check(konsumenter.isNotEmpty()) { "Må sette minst en konsument!" }
    }

    internal fun registerAuthentication(authenticationConfig: AuthenticationConfig, maskinportenJwkProvider: JwkProvider, maskinportenIssuer: String, audience: String) {
        sikkerlogg.info("Registrerer Authentication på id $id for ${konsumenter.joinToString()}")
        authenticationConfig.jwt(id) {
            verifier(maskinportenJwkProvider, maskinportenIssuer) {
                withAudience(audience)
                withClaim("scope") { scopeClaim, jwt ->
                    if (jwt.harIntegrator()) scopeClaim.asString() == delegertScope
                    else scopeClaim.asString() == vanligScope
                }
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
            challenge { _, _ -> call.respondChallenge() }
        }
    }

    internal fun registerApi(routing: Routing, utbetaltePerioder: UtbetaltePerioder, personidentifikatorer: Personidentifikatorer, sporings: Sporingslogg) {
        sikkerlogg.info("Registrerer API på endepunkt /$id for ${konsumenter.joinToString()}")
        routing.authenticate(id) {
            post("/$id/{versjon?}") {
                val requestBody = call.requestBody()
                val versjon = call.versjon()
                val konsument = call.konsument(konsumenter)
                sikkerlogg.info("Mottok request på versjon $versjon fra ${konsument}:\n\t$requestBody")

                val request = konsument.request(requestBody, versjon)

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
            request.berik(this)
            putArray("utbetaltePerioder").addAll(utlevertePerioder)
        }.toString()
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
        private suspend fun ApplicationCall.requestBody() = try { objectMapper.readTree(receiveText()) } catch (throwable: Throwable) {
            objectMapper.createObjectNode()
        }
        private fun ApplicationCall.versjon() = try { parameters["versjon"]?.lowercase()?.removePrefix("v")?.toInt() ?: 1 } catch (throwable: Throwable) { 1 }

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

        private fun ApplicationCall.konsumentOrganisasjonsnummer() =
            principal<JWTPrincipal>()?.payload?.getClaim("consumer")?.asMap()?.get("ID")?.toString()?.substringAfter(":")?.let { Organisasjonsnummer(it)}
        internal fun ApplicationCall.konsumentOrNull() = try {
            konsumentOrganisasjonsnummer()?.let { organisasjonsnummer -> AlleKonsumenter.single { it.organisasjonsnummer == organisasjonsnummer } }
        } catch (_: Throwable) { null }

        private fun ApplicationCall.konsument(konsumenter: Set<Konsument>): Konsument {
            val organisasjonsnummer = konsumentOrganisasjonsnummer() ?: error("Klarte ikke utlede konsuement fra token")
            val konsument = konsumenter.singleOrNull { it.organisasjonsnummer == organisasjonsnummer } ?: error("Konsument med orgnr $organisasjonsnummer er ikke registrert.")
            if (konsument.integrator == null) return konsument
            val integrator = integrator()
            check(integrator == konsument.integrator) { "$integrator er ikke satt som integrator for $konsument" }
            return konsument
        }


        private const val IntegratorClaim = "supplier"
        private fun DecodedJWT.harIntegrator() = getClaim(IntegratorClaim).integratorOrganisasjonsnummer() != null
        private fun Claim.integratorOrganisasjonsnummer() = asMap()?.get("ID")?.toString()?.substringAfter(":")?.let { orgnr -> Organisasjonsnummer(orgnr) }
        private fun ApplicationCall.integrator() = principal<JWTPrincipal>()?.payload?.getClaim(IntegratorClaim)?.integratorOrganisasjonsnummer() ?: error("Klarte ikke utlede integrator fra token")

        private val String.innholdFraResource get() = object {}.javaClass.getResource(this)?.readText() ?: error("Fant ikke resource <$this>")
        private val Map<String, String>.naisFil get() = objectMapper.readTree("/$miljø-nais.json".innholdFraResource)

        internal val Map<String, String>.apis get() = naisFil.path("apis").map { api ->
            val konsumenter = api.path("consumers").map { it.path("organisasjonsnummer").asText() }.map { organisasjonsnummer ->
                AlleKonsumenter.singleOrNull { it.organisasjonsnummer.toString() == organisasjonsnummer } ?: error("Konsument med $organisasjonsnummer er ikke definert")
            }

            val viaIntegratorer = api.path("integratorer").map { it.path("organisasjonsnummer").asText() }.mapNotNull { organisasjonsnummer ->
                AlleKonsumenter.singleOrNull { it.organisasjonsnummer.toString() == organisasjonsnummer }
            }

            Api(id = api.path("id").asText(), scope = api.path("scope").asText(), navn = api.path("navn").asText(), konsumenter = (konsumenter + viaIntegratorer).toSet())
        }
    }
}