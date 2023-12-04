package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")
internal fun HttpRequestBuilder.callId(headernavn: String) = header(headernavn, "${UUID.fromString(MDC.get("callId"))}")
private val String.erUUID get() = kotlin.runCatching { UUID.fromString(this) }.isSuccess

fun main() {
    embeddedServer(ConfiguredCIO, port = 8080, module = Application::spapi).start(wait = true)
}

internal fun Application.spapi(
    config: Map<String, String> = System.getenv(),
    sporings: Sporingslogg = Kafka(config),
    client: HttpClient = HttpClient(CIO),
    accessToken: AccessToken = Azure(config, client),
    utbetaltePerioder: UtbetaltePerioder = Spøkelse(config, client, accessToken),
    personidentifikatorer: Personidentifikatorer = Pdl(config, client, accessToken)
) {

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
        exception<Throwable> { call, cause ->
            sikkerlogg.error("Feil ved håndtering av ${call.request.httpMethod.value} - ${call.request.path()}", cause)
            @Language("JSON")
            val errorResponse = """{"feilmelding": "Uventet feil. Ta kontakt med NAV om feilen vedvarer.", "feilreferanse": "${call.callId}"}"""
            call.respondText(errorResponse, Json, InternalServerError)
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
        swaggerUI(path = "swagger", swaggerFile = "openapi.yml")
        // Endepunkt under /internal eksponeres ikke
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }

        FellesordningenForAfp.setupApi(this) {
            if (prod) return@setupApi call.respond(unavailableForLegalReasons)

            val request = FellesordningenForAfp.request(call)
            val (personidentifikator, fom, tom) = request

            val perioder = utbetaltePerioder.hent(
                personidentifikatorer = personidentifikatorer.hentAlle(personidentifikator, FellesordningenForAfp),
                fom = fom,
                tom = tom
            )

            val response = FellesordningenForAfp.response(perioder, request)

            sporings.logg(
                person = personidentifikator,
                konsument = FellesordningenForAfp,
                leverteData = response
            )

            call.respondText(response, Json)
        }
    }
}

