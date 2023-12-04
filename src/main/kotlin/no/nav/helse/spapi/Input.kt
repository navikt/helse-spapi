package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.lang.IllegalArgumentException

internal class UgyldigInputException(melding: String, cause: Throwable? = null): IllegalArgumentException(melding, cause)

private val objectMapper = jacksonObjectMapper()
private fun JsonNode.hent(path: String) = path(path).takeUnless { it.isMissingNode || it.isNull }
internal suspend fun ApplicationCall.requestBody() = try { objectMapper.readTree(receiveText()) } catch (throwable: Throwable) {
    objectMapper.createObjectNode()
}
internal fun <T> JsonNode.required(path: String, transformer: (jsonNode: JsonNode) -> T): T {
    val jsonNode = hent(path) ?: throw UgyldigInputException("Mangler feltet '$path' i request body.")
    return try { transformer(jsonNode) } catch (throwable: Throwable) {
        throw UgyldigInputException("Ugyldig verdi i feltet '$path' i request body. (var ${jsonNode.asText()})")
    }
}
internal fun <T> JsonNode.optional(path: String, transformer: (jsonNode: JsonNode) -> T): T? {
    val jsonNode = hent(path) ?: return null
    return try { transformer(jsonNode) } catch (throwable: Throwable) {
        throw UgyldigInputException("Ugyldig verdi i feltet '$path' i request body: ${throwable.message} (verdien var ${jsonNode.asText()})")
    }
}