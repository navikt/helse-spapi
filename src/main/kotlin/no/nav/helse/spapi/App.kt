package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spapi.personidentifikator.PdlPersonidentifikatorer
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val objectMapper = jacksonObjectMapper()
internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")

fun main() {
    embeddedServer(ConfiguredCIO, port = 8080, module = Application::spapi).start(wait = true)
}

internal fun Application.spapi(
    config: Map<String, String> = System.getenv(),
    sporings: Sporingslogg = KafkaSporingslogg(config),
    client: HttpClient = HttpClient(CIO),
    accessToken: AccessToken = AzureAccessToken(config, client),
    spøkelse: Spøkelse = RestSpøkelse(config, client, accessToken),
    personidentifikatorer: Personidentifikatorer = PdlPersonidentifikatorer(config, client, accessToken)
) {

    install(CallId) {
        header("x-callId")
        verify { it.isNotEmpty() }
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        logger = sikkerlogg
        level = Level.INFO
        disableDefaultColors()
        callIdMdc("callId")
        filter { call -> !call.request.path().contains("internal") }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            sikkerlogg.info("Feil ved håndtering av ${call.request.httpMethod.value} - ${call.request.path()}", cause)
            call.respondText("Uventet feil. Feilreferanse ${call.callId}. Ta kontakt med NAV om feilen vedvarer.", status = InternalServerError)
        }
    }
    environment.monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    authentication {
        val maskinportenJwkProvider = JwkProviderBuilder(URI(config.hent("MASKINPORTEN_JWKS_URI")).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
        val maskinportenIssuer = config.hent("MASKINPORTEN_ISSUER")
        val audience = config.hent("AUDIENCE")
        FellesordningenForAfp.setupAuthentication(this, maskinportenJwkProvider, maskinportenIssuer, audience)
    }

    val prod = config["NAIS_CLUSTER_NAME"] == "prod-gcp"
    val unavailableForLegalReasons = HttpStatusCode(451, "Unavailable For Legal Reasons")

    routing {
        get("/velkommen") {
            if (prod) return@get call.respond(unavailableForLegalReasons, "451 Unavailable For Legal Reasons: Spaπ blir tilgjenglig i løpet av 2023 👩‍ ⚖️ Gled deg!")
            call.respondText("Velkommen til Spaπ! 👽")
        }
        // Endepunkt under /internal eksponeres ikke
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }

        if (!prod) {
            post("/test") {
                val request = objectMapper.readTree(call.receiveText())
                val personidentifikator = Personidentifikator(request.path("personidentifikator").asText())
                spøkelse.utbetaltePerioder(setOf(personidentifikator), LocalDate.MIN, LocalDate.MAX)
                sporings.logg(personidentifikator, FellesordningenForAfp, """{"perioder":[]}""")
                personidentifikatorer.hentAlle(personidentifikator, FellesordningenForAfp)
                call.respond(OK)
            }
        }

        FellesordningenForAfp.setupApi(this) {
            if (prod) return@setupApi call.respond(unavailableForLegalReasons)

            val (personidentifikator, organisasjonsnummer, fom, tom) = FellesordningenForAfp.request(call)

            val utbetaltePerioder = spøkelse.utbetaltePerioder(
                personidentifikatorer = personidentifikatorer.hentAlle(personidentifikator, FellesordningenForAfp),
                fom = fom,
                tom = tom
            )

            val response = FellesordningenForAfp.response(utbetaltePerioder, organisasjonsnummer)

            sporings.logg(
                person = personidentifikator,
                konsument = FellesordningenForAfp,
                leverteData = response
            )

            call.respondText(response, Json)
        }
    }
}

