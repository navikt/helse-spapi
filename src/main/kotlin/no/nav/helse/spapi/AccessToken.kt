package no.nav.helse.spapi

import com.github.navikt.tbd_libs.azure.createJwkAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.result_object.getOrThrow

interface AccessToken {
    fun get(scope: String): String
}

class Azure: AccessToken {
    private val provider = createJwkAzureTokenClientFromEnvironment()
    override fun get(scope: String) = provider.bearerToken(scope).getOrThrow().token
}