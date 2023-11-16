package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URL
import java.util.concurrent.TimeUnit

private val objectMapper = jacksonObjectMapper()
private fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")

fun main() {
    embeddedServer(ConfiguredCIO, port = 8080, module = Application::spapi).start(wait = true)
}

internal fun Application.spapi(config: Map<String, String> = System.getenv()) {
    authentication {
        jwt(FellesordningenForAfp.id) {
            val jwkProvider = JwkProviderBuilder(URL(config.hent("MASKINPORTEN_JWKS_URI")))
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

    val sporings = Sporingslogg()
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