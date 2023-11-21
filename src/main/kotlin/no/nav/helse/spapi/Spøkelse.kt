package no.nav.helse.spapi

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import java.time.LocalDate

internal interface Spøkelse {
    suspend fun hent(personidentifikator: Personidentifikator, fom: LocalDate, tom: LocalDate): List<Periode>
    class Periode(val fom: LocalDate, val tom: LocalDate, arbeidsgiver: String, grad: Int)
}

internal class RestSpøkelse(config: Map<String, String>, private val client: HttpClient, private val accessToken: AccessToken): Spøkelse {
    private val scope = config.hent("SPOKELSE_SCOPE")

    override suspend fun hent(
        personidentifikator: Personidentifikator,
        fom: LocalDate,
        tom: LocalDate
    ): List<Spøkelse.Periode> {
        val authorizationHeader = "Bearer ${accessToken.get(scope)}"

        val response = client.get("http://spokelse/isalive") {
            header(Authorization, authorizationHeader)
        }
        check(response.status == HttpStatusCode.OK) {
            "Mottok HTTP ${response.status} fra Spøkelse"
        }
        return emptyList()
    }
}