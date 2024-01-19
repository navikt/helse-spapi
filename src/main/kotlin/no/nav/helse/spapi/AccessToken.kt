package no.nav.helse.spapi

import com.github.navikt.tbd_libs.azure.createJwkAzureTokenClientFromEnvironment

interface AccessToken {
    fun get(scope: String): String
}

class Azure: AccessToken {
    private val provider = createJwkAzureTokenClientFromEnvironment()
    override fun get(scope: String) = provider.bearerToken(scope).token
}