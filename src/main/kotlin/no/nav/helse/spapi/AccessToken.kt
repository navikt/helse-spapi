package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Accept
import io.ktor.http.HttpHeaders.ContentType
import java.net.URL

interface AccessToken {
    suspend fun get(scope: String): String
}

internal class AzureAccessToken(config: Map<String, String>): AccessToken {
    private val tokenEndpoint = URL(config.hent("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"))
    private val clientId = config.hent("AZURE_APP_CLIENT_ID")
    private val clientSecret = config.hent("AZURE_APP_CLIENT_SECRET")
    private val client = HttpClient(CIO)

    override suspend fun get(scope: String): String {
        val response = client.post(tokenEndpoint) {
            header(Accept, Json)
            header(ContentType, FormUrlEncoded)
            parameter("client_id", clientId)
            parameter("client_secret", clientSecret)
            parameter("scope", scope)
            parameter("grant_type", "client_credentials")
        }
        val json = objectMapper.readTree(response.readBytes())
        val accessToken = json.path("access_token").asText()
        //val expiresIn = json.path("expires_in").asLong() // TODO: Cache Access Token
        return accessToken
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}