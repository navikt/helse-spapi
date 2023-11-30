package no.nav.helse.spapi

import no.nav.helse.spapi.utbetalteperioder.Spøkelse.Companion.eksponerteTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class EksponerteTagsTest {

    @Test
    fun `eksponerer kun UsikkerSykdomsgrad`() {
        assertEquals(emptySet<String>(), listOf("en", "to", "tre").eksponerteTags)
        assertEquals(emptySet<String>(), emptyList<String>().eksponerteTags)
        assertEquals(setOf("UsikkerSykdomsgrad"), listOf("UsikkerGrad").eksponerteTags)
        assertEquals(setOf("UsikkerSykdomsgrad"), listOf("en", "to", "UsikkerGrad", "tre").eksponerteTags)
        assertEquals(emptySet<String>(), listOf("UsikkerSykdomsgrad").eksponerteTags)
    }
}