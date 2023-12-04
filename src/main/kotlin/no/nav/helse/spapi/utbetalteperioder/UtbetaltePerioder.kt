package no.nav.helse.spapi.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Accept
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import no.nav.helse.spapi.AccessToken
import no.nav.helse.spapi.Organisasjonsnummer
import no.nav.helse.spapi.callId
import no.nav.helse.spapi.hent
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import org.intellij.lang.annotations.Language
import java.time.LocalDate

internal interface UtbetaltePerioder {
    suspend fun hent(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate): List<UtbetaltPeriode>
}

internal class Spøkelse(config: Map<String, String>, private val client: HttpClient, private val accessToken: AccessToken): UtbetaltePerioder {
    private val scope = config.hent("SPOKELSE_SCOPE")

    override suspend fun hent(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate
    ): List<UtbetaltPeriode> {
        val authorizationHeader = "Bearer ${accessToken.get(scope)}"

        val response = client.post("http://spokelse/utbetalte-perioder") {
            header(Authorization, authorizationHeader)
            header(Accept, Json)
            header(ContentType, Json)
            callId("x-callId")
            @Language("JSON")
            val body = """
               {
                  "personidentifikatorer": ${personidentifikatorer.map { "\"$it\"" }},
                  "fom": "$fom",
                  "tom": "$tom"
               } 
            """
            setBody(body)
        }
        check(response.status == HttpStatusCode.OK) {
            "Mottok HTTP ${response.status} fra Spøkelse"
        }
        return objectMapper.readTree(response.readBytes()).path("utbetaltePerioder").map {
            UtbetaltPeriode(
                fom = LocalDate.parse(it.path("fom").asText()),
                tom = LocalDate.parse(it.path("tom").asText()),
                organisasjonsnummer = it.path("organisasjonsnummer").takeUnless { orgnr -> orgnr.isMissingNode || orgnr.isNull }?.let { orgnr -> Organisasjonsnummer(orgnr.asText()) },
                grad = it.path("grad").asInt(),
                tags = it.path("tags").map { tag -> tag.asText() }.eksponerteTags
            )
        }
    }

    internal companion object {
        private val objectMapper = jacksonObjectMapper()
        private val tags = mapOf(
            "UsikkerGrad" to "UsikkerSykdomsgrad"
        )
        internal val List<String>.eksponerteTags get() = intersect(tags.keys).map(tags::getValue).toSet()
    }
}