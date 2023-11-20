package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.Map
import kotlin.collections.set

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
        val response = client.submitForm(
            url = tokenEndpoint.toString(),
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", scope)
                append("grant_type", "client_credentials")
            }
        )
        val json = objectMapper.readTree(response.readBytes())
        val accessToken = json.path("access_token").asText()
        val expiresIn = json.path("expires_in").asLong()
        sikkerlogg.info("TokenResponse=$json, expires_in=$expiresIn")
        return accessToken to expiresIn
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}