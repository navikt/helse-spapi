package no.nav.helse.spapi

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.intellij.lang.annotations.Language
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

internal class Issuer(
    private val navn: String,
    private val audience: String
) {
    private val privateKey: RSAPrivateKey
    private val publicKey: RSAPublicKey
    private val algorithm: Algorithm
    private val wireMockServer: WireMockServer

    init {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(512)

        val keyPair = keyPairGenerator.genKeyPair()
        privateKey = keyPair.private as RSAPrivateKey
        publicKey = keyPair.public as RSAPublicKey
        algorithm = Algorithm.RSA256(publicKey, privateKey)
        wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

    }

    @Language("JSON")
    private fun jwks() = """
   {
       "keys": [
           {
               "kty": "RSA",
               "alg": "RS256",
               "kid": "key-1234",
               "e": "${Base64.getUrlEncoder().encodeToString(publicKey.publicExponent.toByteArray())}",
               "n": "${Base64.getUrlEncoder().encodeToString(publicKey.modulus.toByteArray())}"
           }
       ]
   }
   """

    private fun MutableMap<String, String>.hentOgFjern(key: String) = get(key)?.also { remove(key) }

    internal fun accessToken(claims: Map<String, String> = emptyMap(), organisasjonsnummer: Organisasjonsnummer? = null): String {
        val benyttetClaims = claims.toMutableMap()
        val issuer = benyttetClaims.hentOgFjern("iss") ?: this.navn
        val audience = benyttetClaims.hentOgFjern("aud") ?: this.audience
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withKeyId("key-1234")
            .also { claims.forEach { (key, value) -> it.withClaim(key, value) } }
            .also { organisasjonsnummer?.let { orgnr -> it.withClaim("consumer", mapOf("ID" to "0192:$orgnr"))} }
            .sign(algorithm)
    }
    internal fun jwksUri() = "${wireMockServer.baseUrl()}/jwks"
    internal fun navn() = navn
    internal fun audience() = audience
    internal fun start(): Issuer {
        wireMockServer.start()
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/jwks")).willReturn(WireMock.okJson(jwks())))
        return this
    }
    internal fun stop() = wireMockServer.stop()
}