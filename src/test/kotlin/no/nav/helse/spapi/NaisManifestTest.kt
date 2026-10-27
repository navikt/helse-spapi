package no.nav.helse.spapi

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import kotlin.test.assertEquals

class NaisManifestTest {
    @ParameterizedTest
    @CsvSource("dev,dev-gcp", "prod,prod-gcp")
    fun `maskinporten-scopene i nais-manifestet stemmer med konsumentene appen registrerer`(
        miljø: String,
        naisMiljø: String,
    ) {
        val apis = jacksonObjectMapper().readTree(File("src/main/resources/$miljø-nais.json")).path("apis")
        val manifest = YAMLMapper().readTree(File(".nais/spapi.$naisMiljø.yaml"))

        assertEquals(forventedeScopes(apis), manifest.at("/spec/maskinporten/scopes/exposes"))
    }

    private fun forventedeScopes(apis: JsonNode) =
        JsonNodeFactory.instance.arrayNode().apply {
            apis.forEach { api ->
                add(scope(api.path("scope").asString(), api.path("consumers")))
                if (api.path("enableIntegratorer").asBoolean()) {
                    add(
                        scope("delegert${api.path("scope").asString()}", api.path("integratorer")).apply {
                            put("separator", "/")
                            put("delegationSource", "altinn")
                        },
                    )
                }
            }
        }

    private fun scope(
        navn: String,
        konsumenter: JsonNode,
    ): ObjectNode =
        JsonNodeFactory.instance.objectNode().apply {
            put("name", navn)
            put("enabled", true)
            put("product", "sykepenger")
            putArray("consumers").apply {
                konsumenter.forEach { konsument ->
                    addObject()
                        .put("name", konsument.path("navn").asString())
                        .put("orgno", konsument.path("organisasjonsnummer").asString())
                }
            }
        }
}
