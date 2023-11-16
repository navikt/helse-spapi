package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType.Application.Json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URL

private val objectMapper = jacksonObjectMapper()
private fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")

fun main() {
    embeddedServer(CIO, port = 8080, module = Application::spapi).start(wait = true)
}

internal fun Application.spapi(config: Map<String, String> = System.getenv()) {
    authentication {
        jwt(FellesordningenForAfp.id) {
            val jwkProvider = JwkProviderBuilder(URL(config.hent("MASKINPORTEN_JWKS_URI"))).build()
            verifier(jwkProvider, config.hent("MASKINPORTEN_ISSUER")) {
                withAudience(config.hent("AUDIENCE"))
                withClaim("scope", FellesordningenForAfp.scope)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
            challenge { _, _ -> call.respondChallenge() }
        }
    }

    val sporings = Sporingslogg()

    routing {
        get("/velkommen") { call.respondText("Velkommen til Spaπ! 👽") }
        // Endepunkt under /internal eksponeres ikke
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }

        authenticate(FellesordningenForAfp.id) {
            post(FellesordningenForAfp.endepunkt) {
                val request = objectMapper.readTree(call.receiveText())
                val response = """{"perioder":[]}"""
                val person = Personidentifikator(request.path("personidentifikator").asText())
                sporings.logg(person = person, konsument = FellesordningenForAfp, leverteData = response)
                call.respondText(response, Json)
            }
        }
    }
}