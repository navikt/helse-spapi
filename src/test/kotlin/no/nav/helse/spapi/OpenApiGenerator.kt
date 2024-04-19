package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import kotlinx.coroutines.runBlocking
import no.nav.helse.spapi.Konsument.Companion.fellesApi
import no.nav.helse.spapi.Konsument.Companion.konsumenter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class OpenApiGenerator {

    @Test
    fun `generere openapi for dev`() {
        lagOpenapiFil(emptyMap())
    }

    @Test
    fun `generere openapi for prod`() {
        lagOpenapiFil(mapOf("NAIS_CLUSTER_NAME" to "prod-gcp"))
    }

    private fun lagOpenapiFil(config: Map<String, String>) {
        val konsumenter = config.konsumenter.map { mapOf(
            "id" to it.id,
            "navn" to it.navn,
            "scope" to it.scope,
            "organisasjonsnummer" to it.organisasjonsnummer,
            "suffix" to it.suffix
        )}

        val path = "src/main/resources/${config.miljø}-openapi.yml".absolutePath

        val yml = Handlebars(ClassPathTemplateLoader("/", ".yml")).compile("openapi-template").apply(mapOf(
            "konsumenter" to (konsumenter + fellesApi(config)).filterNotNull(),
            "prod" to (config.miljø == "prod")
        ))

        path.writeBytes(yml.toByteArray())
    }

    private fun fellesApi(config: Map<String, String>): Map<String, Any>? {
        if (!config.fellesApi) return null
        return mapOf(
            "id" to "avtalefestet-pensjon",
            "navn" to "Avtalefestet pensjon",
            "scope" to "nav:sykepenger:avtalefestetpensjon.read",
            "organisasjonsnummer" to config.konsumenter.joinToString { it.organisasjonsnummer.toString() },
            "suffix" to "V1"
        )
    }

    private companion object {
        private val String.absolutePath get() = Paths.get("${Paths.get("").absolutePathString()}/$this")
        @Language("JSON")
        private val tullerequest = """
            { "personidentifikator": "11111111111", "organisasjonsnummer": "999999999", "fraOgMedDato": "2018-01-01", "tilOgMedDato": "2018-12-31", "minimumSykdomsgrad": 80 }
        """.let { jacksonObjectMapper().readTree(it) }
        private val Konsument.suffix get() = when (val request = runBlocking { request(tullerequest) } ) {
            is RequiredOrganisasjonsnummerOptionalMinimumSykdomsgrad -> "V1"
            else -> error("Mangler suffix for ${request.javaClass.simpleName}")
        }
    }
}