package no.nav.helse.spapi.personidentifikator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.spapi.AccessToken
import no.nav.helse.spapi.Konsument
import no.nav.helse.spapi.callId
import no.nav.helse.spapi.hent
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

internal interface Personidentifikatorer {
    suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument): Set<Personidentifikator>
}

internal class Pdl(config: Map<String, String>, private val httpClient: HttpClient, private val accessToken: AccessToken): Personidentifikatorer {
    private val url = "https://${config.hent("PDL_HOST")}/graphql"
    private val scope = config.hent("PDL_SCOPE")

    override suspend fun hentAlle(personidentifikator: Personidentifikator, konsument: Konsument): Set<Personidentifikator> {
        val response = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer ${accessToken.get(scope)}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json)
            callId("Nav-Call-Id")
            header("behandlingsnummer", konsument.behandlingsnummer)
            setBody(body(personidentifikator))
        }

        check(response.status == HttpStatusCode.OK) {
            "Mottok HTTP ${response.status} fra PDL:\n\t${response.bodyAsText()}"
        }

        val json = objectMapper.readTree(response.readRawBytes())

        sikkerlogg.info("Response fra PDL:\n\t$json")

        return json.personidentifikatorer + personidentifikator
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
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
