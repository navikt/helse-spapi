package no.nav.helse.spapi

import com.github.navikt.tbd_libs.signed_jwt_issuer_test.Issuer

internal fun Issuer.maskinportenAccessToken(
    claims: Map<String,String> = emptyMap(),
    konsument: Organisasjonsnummer? = null,
    integrator: Organisasjonsnummer? = null
) = accessToken {
    claims.forEach(::withClaim)
    konsument?.let { orgnr -> withClaim("consumer", mapOf("ID" to "0192:$orgnr")) }
    integrator?.let { orgnr -> withClaim("supplier", mapOf("ID" to "0192:$orgnr")) }
}
