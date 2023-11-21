package no.nav.helse.spapi.personidentifikator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.spapi.AccessToken
import no.nav.helse.spapi.Konsument
import no.nav.helse.spapi.hent
import org.intellij.lang.annotations.Language
import java.util.UUID

internal interface Personidentifikatorer {
    suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument): Set<Personidentifikator>
}

internal class PdlPersonidentifikatorer(config: Map<String, String>, private val httpClient: HttpClient, private val accessToken: AccessToken): Personidentifikatorer {
    private val scope = config.hent("PDL_SCOPE")

    override suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument): Set<Personidentifikator> {
        val response = httpClient.post {
            header(HttpHeaders.Authorization, "Bearer ${accessToken.get(scope)}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header("Nav-Call-Id", "${UUID.randomUUID()}")
            header("behandlingsnummer", konsument.behandlingsnummer)
            setBody(body(personidentifikator))
        }

        check(response.status == HttpStatusCode.OK) {
            "Mottok HTTP ${response.status} fra PDL"
        }

        val json = objectMapper.readTree(response.readBytes())


        return json.personidentifikatorer.also {
            check(it.contains(personidentifikator)) { "Responsen fra PDL inneholder _ikke_ personidentifikatoren vi spurte på.." }
        }
    }

    internal companion object {
        private val objectMapper = jacksonObjectMapper()
        private fun String.formaterQuery() = replace("[\n\r]".toRegex(), "").replace("\\s{2,}".toRegex(), " ")

        private const val DOLLAR = "$"
        private const val QUERY = "query(${DOLLAR}ident: ID!) { hentIdenter(ident: ${DOLLAR}ident, historikk: true, grupper: [FOLKEREGISTERIDENT]) { identer { ident } } }"

        @Language("JSON")
        private fun body(personidentifikator: Personidentifikator) = """
              {
                "query": "${QUERY.formaterQuery()}",
                "variables": {
                  "ident": "$personidentifikator"
                }
              }
        """

        internal val JsonNode.personidentifikatorer get() = this
            .path("data")
            .path("hentIdenter")
            .path("identer")
            .map { Personidentifikator(it.path("ident").asText()) }
            .toSet()
    }
}
