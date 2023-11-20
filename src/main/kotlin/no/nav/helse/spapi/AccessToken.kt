package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Accept
import io.ktor.http.HttpHeaders.ContentType
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.concurrent.ConcurrentHashMap

abstract class AccessToken(private val leeway: Duration = Duration.ofSeconds(30)) {
    private var cache = ConcurrentHashMap<String, Pair<String, LocalDateTime>>()
    abstract suspend fun hentNytt(scope: String): Pair<String, Long>

    internal suspend fun get(scope: String) =
        cache[scope]?.takeIf { it.second > now() }?.first ?: hentOgCache(scope)

    private suspend fun hentOgCache(scope: String): String {
        val (accessToken, expiresIn) = hentNytt(scope)
        val expires = now().plusSeconds(expiresIn).minus(leeway)
        cache[scope] = accessToken to expires
        logger.info("Hentet nytt access token for $scope som brukes frem til $expires")
        return accessToken
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(AccessToken::class.java)
    }
}


internal class AzureAccessToken(config: Map<String, String>, private val client: HttpClient): AccessToken() {
    private val tokenEndpoint = URL(config.hent("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"))
    private val clientId = config.hent("AZURE_APP_CLIENT_ID")
    private val clientSecret = config.hent("AZURE_APP_CLIENT_SECRET")

    override suspend fun hentNytt(scope: String): Pair<String, Long> {
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
        val expiresIn = json.path("expires_in").asLong()
        return accessToken to expiresIn
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}