package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spapi.personidentifikator.Pdl.Companion.personidentifikatorer
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PdlTest {

    @Test
    fun `oversetter response fra PDL til personidentifikatorer`() {

        @Language("JSON")
        val response = """
        {
          "data": {
            "hentIdenter": {
              "identer": [
                {
                  "ident": "12345678901",
                  "gruppe": "FOLKEREGISTERIDENT",
                  "historisk": true
                },
                            {
                  "ident": "22345678901",
                  "gruppe": "FOLKEREGISTERIDENT",
                  "historisk": true
                },
                {
                  "ident": "32345678901",
                  "gruppe": "FOLKEREGISTERIDENT",
                  "historisk": false
                }
              ]
            }
          }
        }
        """.let { jacksonObjectMapper().readTree(it) }

        assertEquals(setOf(Personidentifikator("12345678901"), Personidentifikator("22345678901"), Personidentifikator("32345678901")), response.personidentifikatorer)
    }
}