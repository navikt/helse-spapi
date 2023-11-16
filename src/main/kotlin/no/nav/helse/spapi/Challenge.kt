package no.nav.helse.spapi

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.util.*

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

internal suspend fun ApplicationCall.respondChallenge() {
    try {
        val jwt = request.header(HttpHeaders.Authorization)
            ?.substringAfter("Bearer ")
            ?.split(".")
            ?.takeIf { it.size == 3 }
            ?: return respond(Unauthorized, "Bearer token må settes i Authorization header for å hente data fra Spaπ!")
        sikkerlogg.error("Mottok request med access token som ikke har tilgang til Spaπ sitt endepunkt ${request.path()}!\n\tJWT Headers: ${String(Base64.getUrlDecoder().decode(jwt[0]))}\n\tJWT Payload: ${String(Base64.getUrlDecoder().decode(jwt[1]))}")
        respond(Forbidden, "Bearer token som er brukt har ikke rett tilgang til å hente data fra Spaπ! Ta kontakt med NAV.")
    } catch (throwable: Throwable) {
        respond(Unauthorized, "Bearer token må settes i Authorization header for å hente data fra Spaπ!")
    }
}