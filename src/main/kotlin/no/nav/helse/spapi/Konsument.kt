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
import no.nav.helse.spapi.Spøkelse.Periode
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import org.intellij.lang.annotations.Language
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
    internal data class Request(val personidentifikator: Personidentifikator, val organisasjonsnummer: Organisasjonsnummer, val fom: LocalDate, val tom: LocalDate) {
        init { check(fom <= tom) { "Ugyldig periode $fom - $tom"} }
    }
    internal suspend fun request(call: ApplicationCall): Request {
        val request = objectMapper.readTree(call.receiveText())
        val personidentifikator = Personidentifikator(request.path("personidentifikator").asText())
        val organisasjonsnummer = Organisasjonsnummer(request.path("organisasjonsnummer").asText())
        val fom = LocalDate.parse(request.path("fom").asText())
        val tom = LocalDate.parse(request.path("tom").asText())
        return Request(personidentifikator, organisasjonsnummer, fom, tom)
    }

    // TODO: Her skal vi nok filtrere på organisasjonsnummer
    @Language("JSON")
    internal fun response(utbetaltePerioder: List<Periode>, organisasjonsnummer: Organisasjonsnummer) = """
        {
          "utbetaltePerioder": ${utbetaltePerioder.filter { it.grad >= 80 }.map { """{ "fom": "${it.fom}", "tom": "${it.tom}"}""" }}
        }
    """
}

