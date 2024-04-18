package no.nav.helse.spapi

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.JsonNode
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

internal abstract class Konsument(
    internal val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val id: String,
    internal val scope: String,
    internal val behandlingsnummer: String ,
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
                val requestBody = call.requestBody()
                sikkerlogg.info("Mottok request fra ${this@Konsument}:\n\t$requestBody")
                val utledetKonsument = call.utledKonsument()
                if (utledetKonsument != this@Konsument) sikkerlogg.warn("Utledet konsument er $utledetKonsument. Forventet ${this@Konsument}")

                val request = request(requestBody)

                val perioder = utbetaltePerioder.hent(
                    personidentifikatorer = personidentifikatorer.hentAlle(request.personidentifikator, this@Konsument),
                    fom = request.fom,
                    tom = request.tom
                )

                val response = response(perioder, request)

                sporings.logg(
                    person = request.personidentifikator,
                    konsument = this@Konsument,
                    leverteData = response
                )

                call.respondText(response, ContentType.Application.Json)
            }
        }
    }

    open suspend fun request(requestBody: JsonNode): KonsumentRequest = RequiredOrganisasjonsnummerOptionalMinimumSykdomsgrad(requestBody)

    private fun response(utbetaltePerioder: List<UtbetaltPeriode>, request: KonsumentRequest): String {
        val utlevertePerioder = request.filtrer(utbetaltePerioder).map { objectMapper.readTree(request.json(it)) }
        return objectMapper.createObjectNode().apply {
            putArray("utbetaltePerioder").addAll(utlevertePerioder)
        }.toString()
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
        private suspend fun ApplicationCall.requestBody() = try { objectMapper.readTree(receiveText()) } catch (throwable: Throwable) {
            objectMapper.createObjectNode()
        }

        private val String.innholdFraResource get() = object {}.javaClass.getResource(this)?.readText() ?: error("Fant ikke resource <$this>")

        private val AlleKonsumenter = setOf(
            FellesordningenForAfp,
            KommunalLandspensjonskasse,
            StatensPensjonskasse,
            StorebrandPensjonstjenester,
            StorebrandLivsforsikring,
            OsloPensjonsforsikring,
            GablerPensjonstjenester,
            Aksio
        )
        internal val Map<String, String>.konsumenter get() = objectMapper
            .readTree("/$miljø-nais.json".innholdFraResource)
            .path("consumers")
            .associate { Organisasjonsnummer(it.path("orgno").asText()) to "nav:sykepenger:${it.path("scope").asText()}" }
            .map { (organisasjonsnummer, scope) ->
                AlleKonsumenter.singleOrNull { it.organisasjonsnummer == organisasjonsnummer && it.scope == scope } ?: error("Fant ikke konsument med orgnr $organisasjonsnummer og scope $scope")
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
        private fun ApplicationCall.utledKonsument(): Konsument? {
            val organisasjonsnummer = principal<JWTPrincipal>()?.payload?.getClaim("consumer")?.asMap()?.get("ID")?.toString()?.substringAfter(":") ?: return null
            return AlleKonsumenter.firstOrNull { it.organisasjonsnummer.toString() == organisasjonsnummer }
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
)
internal object OsloPensjonsforsikring: Konsument(
    navn = "Oslo pensjonsforsikring",
    organisasjonsnummer = Organisasjonsnummer("982759412"),
    id = "oslo-pensjonsforsikring",
    scope = "nav:sykepenger:oslopensjonsforsikring.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StatensPensjonskasse: Konsument(
    navn = "Statens pensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("982583462"),
    id = "statens-pensjonskasse",
    scope = "nav:sykepenger:statenspensjonskasse.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StorebrandLivsforsikring: Konsument(
    navn = "Storebrand livsforsikring",
    organisasjonsnummer = Organisasjonsnummer("958995369"),
    id = "storebrand-livsforsikring",
    scope = "nav:sykepenger:storebrandlivsforsikring.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object KommunalLandspensjonskasse: Konsument(
    navn = "Kommunal landspensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("938708606"),
    id = "kommunal-landspensjonskasse",
    scope = "nav:sykepenger:kommunallandspensjonskasse.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StorebrandPensjonstjenester: Konsument(
    navn = "Storebrand pensjonstjenester",
    organisasjonsnummer = Organisasjonsnummer("931936492"),
    id = "storebrand-pensjonstjenester",
    scope = "nav:sykepenger:storebrandpensjonstjenester.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object GablerPensjonstjenester: Konsument(
    navn = "Gabler pensjonstjenester",
    organisasjonsnummer = Organisasjonsnummer("916833520"),
    id = "gabler-pensjonstjenester",
    scope = "nav:sykepenger:gablerpensjonstjenester.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object Aksio: Konsument(
    navn = "Aksio",
    organisasjonsnummer = Organisasjonsnummer("927613298"),
    id = "aksio",
    scope = "nav:sykepenger:aksio.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)