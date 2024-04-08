package no.nav.helse.spapi

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import no.nav.helse.spapi.EnKonsument.Companion.konsumenter
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
            "organisasjonsnummer" to it.organisasjonsnummer
        )}

        val path = "src/main/resources/${config.miljø}-openapi.yml".absolutePath

        val yml = Handlebars(ClassPathTemplateLoader("/", ".yml")).compile("openapi-template").apply(mapOf(
            "konsumenter" to konsumenter,
            "prod" to (config.miljø == "prod")
        ))

        path.writeBytes(yml.toByteArray())
    }

    private val String.absolutePath get() = Paths.get("${Paths.get("").absolutePathString()}/$this")
}