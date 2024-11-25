package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.naisful.NaisEndpoints
import com.github.navikt.tbd_libs.naisful.plainApp
import com.github.navikt.tbd_libs.naisful.standardApiModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.helse.spapi.Api.Companion.apis
import no.nav.helse.spapi.Api.Companion.konsumentOrNull
import no.nav.helse.spapi.personidentifikator.Pdl
import no.nav.helse.spapi.personidentifikator.Personidentifikatorer
import no.nav.helse.spapi.utbetalteperioder.Spøkelse
import no.nav.helse.spapi.utbetalteperioder.UtbetaltePerioder
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private fun ApplicationCall.loggHåndtertFeil(melding: String?) = sikkerlogg.warn("Feil i request til ${request.httpMethod.value} - ${request.path()}: $melding")

internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")
internal val Map<String, String>.miljø get() = if (get("NAIS_CLUSTER_NAME")?.lowercase()?.contains("prod") == true) "prod" else "dev"
internal fun HttpRequestBuilder.callId(headernavn: String) = header(headernavn, "${UUID.fromString(MDC.get("x-callId"))}")
private suspend fun ApplicationCall.respondError(status: HttpStatusCode, melding: String? = null) {
    val feilmelding = melding ?: "Uventet feil. Ta kontakt med NAV om feilen vedvarer."
    @Language("JSON")
    val errorResponse = """{"feilmelding": "$feilmelding", "feilreferanse": "$callId"}"""
    respondText(errorResponse, Json, status)
}

fun main() {
    spapiApp().start(wait = true)
}

internal fun spapiApp() = plainApp(
    applicationLogger = sikkerlogg,
    cioConfiguration = {
        val customParallelism = 16
        connectionGroupSize = customParallelism / 2 + 1
        workerGroupSize  = customParallelism / 2 + 1
        callGroupSize = customParallelism
    },
    applicationModule = { spapi() }
)
internal fun Application.spapi(
    config: Map<String, String> = System.getenv(),
    sporings: Sporingslogg = Kafka(config),
    client: HttpClient = HttpClient(CIO),
    accessToken: AccessToken = Azure(),
    utbetaltePerioder: UtbetaltePerioder = Spøkelse(config, client, accessToken),
    personidentifikatorer: Personidentifikatorer = Pdl(config, client, accessToken)
) {
    standardApiModule(
        meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM),
        objectMapper = jacksonObjectMapper(),
        callLogger = sikkerlogg,
        naisEndpoints = NaisEndpoints(
            isaliveEndpoint = "/internal/isalive",
            isreadyEndpoint = "/internal/isready",
            metricsEndpoint = "/internal/metrics",
            preStopEndpoint = "/internal/stop"
        ),
        callIdHeaderName = "x-callId",
        statusPagesConfig = {
            exception<UgyldigInputException> { call, cause ->
                call.loggHåndtertFeil(cause.message)
                call.respondError(BadRequest, cause.message)
            }
            exception<Throwable> { call, cause ->
                sikkerlogg.error("Feil ved håndtering av ${call.request.httpMethod.value} - ${call.request.path()}", cause)
                call.respondError(InternalServerError)
            }
        },
        timersConfig = { call,_ -> this.tag("konsument", call.konsumentOrNull()?.navn ?: "n/a") },
        mdcEntries = mapOf("konsument" to { call: ApplicationCall -> call.konsumentOrNull()?.navn ?: "n/a" })
    )
    install(RateLimit) {
        register(RateLimitName("api")) {
            rateLimiter(limit = 1200, refillPeriod = 60.seconds)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
    monitor.subscribe(ApplicationStopped) {
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
        rateLimit(RateLimitName("api")) {
            apier.forEach { it.registerApi(this@routing, utbetaltePerioder, personidentifikatorer, sporings) }
        }
    }
}
