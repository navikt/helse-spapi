package no.nav.helse.spapi

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing


fun main() {
    embeddedServer(CIO, port = 8080, module = Application::spapi).start(wait = true)
}

private fun Application.spapi() {
    routing {
        get("/internal/isalive") { call.respondText("ISALIVE") }
        get("/internal/isready") { call.respondText("READY") }
    }
}