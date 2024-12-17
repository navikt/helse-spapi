package no.nav.helse.spapi

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import org.intellij.lang.annotations.Language
import org.skyscreamer.jsonassert.JSONAssert
import kotlin.test.assertEquals

internal data class SpapiTestContext(
    private val maskinporten: Issuer,
    private val feilIssuer: Issuer,
    private val client: HttpClient,
    private val konsument: Organisasjonsnummer,
    private val endepunkt: String,
    private val scope: String,
    private val integrator: Organisasjonsnummer?
) {
    private fun riktigToken() = maskinporten.accessToken(mapOf("scope" to scope), konsument, integrator)

    suspend fun request(
        personidentifikator: String = "11111111111",
        organisasjonsnummer: String = "999999999",
        accessToken: String? = riktigToken(),
        minimumSykdomsgrad: Int? = null,
        fom: String = "2018-01-01",
        tom: String = "2018-01-31",
        saksId: String? = "jeg-er-en-saksId",
        assertions: suspend HttpResponse.() -> Unit
    ) {
        @Language("JSON")
        val body = """
            {
              "personidentifikator": "$personidentifikator",
              "organisasjonsnummer": "$organisasjonsnummer",
              "fraOgMedDato": "$fom",
              "tilOgMedDato": "$tom"
              ${if (minimumSykdomsgrad != null) ",\"minimumSykdomsgrad\": $minimumSykdomsgrad" else ""}
              ${if (saksId != null) ",\"saksId\": \"$saksId\"" else ""}
            }
        """
        request(accessToken, body, assertions)
    }

    suspend fun request(
        accessToken: String?,
        body: String,
        assertions: suspend HttpResponse.() -> Unit
    ) = assertions(client.post(endepunkt) {
        accessToken?.let { header(Authorization, "Bearer $it") }
        setBody(body)
    })

    suspend fun HttpResponse.assertResponse(forventet: String) = JSONAssert.assertEquals(forventet, bodyAsText(), true)
    fun HttpResponse.assertStatus(forventet: HttpStatusCode) = assertEquals(forventet, status)
}

