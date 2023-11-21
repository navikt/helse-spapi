package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spapi.personidentifikator.Personidentifikator
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
    spøkelse: Spøkelse = RestSpøkelse(config, client, accessToken)
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
            sikkerlogg.info("Feil ved håndtering av ${call.request.httpMethod} - ${call.request.path()}", cause)
            call.respondText("Uventet feil. Feilreferanse ${call.callId}. Ta kontakt med NAV om feilen vedvarer.", status = InternalServerError)
        }
    }
    environment.monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    authentication {
        jwt(FellesordningenForAfp.id) {
            val jwkProvider = JwkProviderBuilder(URI(config.hent("MASKINPORTEN_JWKS_URI")).toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()

            verifier(jwkProvider, config.hent("MASKINPORTEN_ISSUER")) {
                withAudience(config.hent("AUDIENCE"))
                withClaim("scope", FellesordningenForAfp.scope)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
            challenge { _, _ -> call.respondChallenge() }
        }
    }

    val prod = config["NAIS_CLUSTER_NAME"] == "prod-gcp"
    val unavailableForLegalReasons = HttpStatusCode(451, "Unavailable For Legal Reasons")

    routing {
        get("/velkommen") {
            if (prod) return@get call.respond(unavailableForLegalReasons, "451 Unavailable For Legal Reasons: Spaπ blir tilgjenglig i løpet av 2023 👩‍ ⚖️ Gled deg!")
            spøkelse.hent(Personidentifikator("11111111111"), LocalDate.MIN, LocalDate.MAX).also { sikkerlogg.info("Å kontakte Spøkelse gikk jo bra!") }
            call.respondText("Velkommen til Spaπ! 👽")
        }
        // Endepunkt under /internal eksponeres ikke
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }

        authenticate(FellesordningenForAfp.id) {
            post(FellesordningenForAfp.endepunkt) {
                if (prod) return@post call.respond(unavailableForLegalReasons)
                val request = objectMapper.readTree(call.receiveText())
                val response = """{"perioder":[]}"""
                val person = Personidentifikator(request.path("personidentifikator").asText())
                sporings.logg(person = person, konsument = FellesordningenForAfp, leverteData = response)
                call.respondText(response, Json)
            }
        }
    }
}