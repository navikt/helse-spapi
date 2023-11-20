package no.nav.helse.spapi

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

internal class AccessTokenTest {

    @Test
    fun `Access tokens caches per scope`() {
        val accessToken = object : AccessToken(Duration.ofSeconds(1)) {
            override suspend fun hentNytt(scope: String): Pair<String, Long> =
                "${UUID.randomUUID()}" to 2L
        }

        runBlocking {
            val scopeA = "scopeA"
            val scopeB = "scopeB"
            val accessToken1ScopeA = accessToken.get(scopeA)
            val accessToken2ScopeA = accessToken.get(scopeA)
            val accessTokenScopeB = accessToken.get(scopeB)
            assertEquals(accessToken1ScopeA, accessToken2ScopeA)
            assertNotEquals(accessToken1ScopeA, accessTokenScopeB)
            delay(2000L)
            val accessToken3ScopeA = accessToken.get(scopeA)
            assertNotEquals(accessToken2ScopeA, accessToken3ScopeA)
        }
    }
}