package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spapi.Api.Companion.apis
import no.nav.helse.spapi.personidentifikator.Pdl
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import no.nav.helse.spapi.utbetalteperioder.Spøkelse
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")
internal val Map<String, String>.miljø get() = if (get("NAIS_CLUSTER_NAME")?.lowercase()?.contains("prod") == true) "prod" else "dev"
internal fun HttpRequestBuilder.callId(headernavn: String) = header(headernavn, "${UUID.fromString(MDC.get("callId"))}")
private val String.erUUID get() = kotlin.runCatching { UUID.fromString(this) }.isSuccess
private suspend fun ApplicationCall.respondError(status: HttpStatusCode, melding: String? = null) {
    val feilmelding = melding ?: "Uventet feil. Ta kontakt med NAV om feilen vedvarer."
    @Language("JSON")
    val errorResponse = """{"feilmelding": "$feilmelding", "feilreferanse": "$callId"}"""
    respondText(errorResponse, Json, status)
}

fun main() {
    embeddedServer(ConfiguredCIO, port = 8080, module = Application::spapi).start(wait = true)
}

internal fun Application.spapi(
    config: Map<String, String> = System.getenv(),
    sporings: Sporingslogg = Kafka(config),
    client: HttpClient = HttpClient(CIO),
    accessToken: AccessToken = Azure(),
    utbetaltePerioder: UtbetaltePerioder = Spøkelse(config, client, accessToken),
    personidentifikatorer: Personidentifikatorer = Pdl(config, client, accessToken)
) {
    install(RateLimit) {
        global {
            rateLimiter(limit = 50, refillPeriod = 60.seconds)
            requestKey {  call -> call.request.origin.remoteAddress }
        }
    }

    install(CallId) {
        header("x-callId")
        verify { it.erUUID }
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
        exception<UgyldigInputException> { call, cause ->
            sikkerlogg.warn("Feil i request til ${call.request.httpMethod.value} - ${call.request.path()}: ${cause.message}")
            call.respondError(BadRequest, cause.message)
        }
        exception<Throwable> { call, cause ->
            sikkerlogg.error("Feil ved håndtering av ${call.request.httpMethod.value} - ${call.request.path()}", cause)
            call.respondError(InternalServerError)
        }
    }
    environment.monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    val apier = config.apis

    authentication {
        val maskinportenJwkProvider = JwkProviderBuilder(URI(config.hent("MASKINPORTEN_JWKS_URI")).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
        val maskinportenIssuer = config.hent("MASKINPORTEN_ISSUER")
        val audience = config.hent("AUDIENCE")
        apier.forEach { it.registerAuthentication(this, maskinportenJwkProvider, maskinportenIssuer, audience) }
    }

    routing {
        get("/velkommen") { call.respondText("Velkommen til Spaπ! 👽") }
        swaggerUI(path = "swagger", swaggerFile = "${config.miljø}-openapi.yml")
        // Endepunkt under /internal eksponeres ikke
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }
        apier.forEach { it.registerApi(this, utbetaltePerioder, personidentifikatorer, sporings) }
    }
}

