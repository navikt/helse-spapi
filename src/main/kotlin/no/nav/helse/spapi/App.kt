package no.nav.helse.spapi

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import java.net.URL

private fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")
private const val FellesordningenForAfp = "fellesordningen-for-afp"

fun main() {
    embeddedServer(CIO, port = 8080, module = Application::spapi).start(wait = true)
}

internal fun Application.spapi(config: Map<String, String> = System.getenv()) {
    authentication {
        jwt(FellesordningenForAfp) {
            val jwkProvider = JwkProviderBuilder(URL(config.hent("MASKINPORTEN_JWKS_URI"))).build()
            verifier(jwkProvider, config.hent("MASKINPORTEN_ISSUER")) {
                withAudience(config.hent("AUDIENCE"))
                withClaim("scope", "nav:sykepenger:fellesordningenforafp.read")
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
        }
    }

    routing {
        get("/velkommen") { call.respondText("Velkommn til Spapi! 👽") }
        // Endepunkt under /internal eksponeres ikke
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }

        authenticate(FellesordningenForAfp) {
            post("/$FellesordningenForAfp") {
                call.respondText("Hei til dere Fellesordningen for AFP! 👽")
            }
        }
    }
}