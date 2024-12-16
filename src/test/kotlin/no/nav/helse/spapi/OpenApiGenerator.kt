package no.nav.helse.spapi

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import no.nav.helse.spapi.Api.Companion.apis
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
        val apis = config.apis.map { api ->
            mapOf(
                "id" to api.id,
                "scopes" to api.scopes,
                "navn" to api.navn,
                "versjon" to if (api.id == "avtalefestet-pensjon") "V2" else "V1",
                "saksIdInfo" to saksIdInfo(api.konsumenter, config.miljø),
                "organisasjonsnummer" to api.konsumenter.joinToString { it.organisasjonsnummer.toString() }
            )
        }

        val path = "src/main/resources/${config.miljø}-openapi.yml".absolutePath

        val yml = Handlebars(ClassPathTemplateLoader("/", ".yml")).compile("openapi-template").apply(mapOf(
            "apis" to apis,
            "prod" to (config.miljø == "prod")
        ))

        path.writeBytes(yml.toByteArray())
    }

    private fun Set<Konsument>.inneholder(orgnr: String) = firstOrNull { it.organisasjonsnummer.toString() == orgnr } != null
    private fun saksIdInfo(konsumenter: Set<Konsument>, miljø: String) = listOfNotNull(
        "Feltet `saksId` _må_ settes, med unntak for visse konsumenter;",
        "✅ Statens pensjonskasse (982583462) - Vil bli påkrevd 1.Februar 2025.".takeIf { konsumenter.inneholder("982583462") },
        "✅ Kommunal landspensjonskasse (938708606) - Har integrert uten `saksId`, avventer bekreftelse på at de legger det ved.".takeIf { konsumenter.inneholder("938708606") && miljø == "dev"},
        "❌ Øvrig konsumenter vil få en HTTP 400 om `saksId` mangler"
    ).takeUnless { it.size == 2 } ?: emptyList()

    private companion object {
        private val String.absolutePath get() = Paths.get("${Paths.get("").absolutePathString()}/$this")
    }
}