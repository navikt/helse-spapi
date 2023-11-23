package no.nav.helse.spapi

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import no.nav.helse.spapi.Spøkelse.Periode
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import org.intellij.lang.annotations.Language
import java.time.LocalDate

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

