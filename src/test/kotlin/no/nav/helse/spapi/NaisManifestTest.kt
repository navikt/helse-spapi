package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
        val manifest = ObjectMapper(YAMLFactory()).readTree(File(".nais/spapi.$naisMiljø.yaml"))

        assertEquals(forventedeScopes(apis), manifest.at("/spec/maskinporten/scopes/exposes"))
    }

    private fun forventedeScopes(apis: JsonNode) =
        JsonNodeFactory.instance.arrayNode().apply {
            apis.forEach { api ->
                add(scope(api.path("scope").asText(), api.path("consumers")))
                if (api.path("enableIntegratorer").asBoolean()) {
                    add(
                        scope("delegert${api.path("scope").asText()}", api.path("integratorer")).apply {
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
                        .put("name", konsument.path("navn").asText())
                        .put("orgno", konsument.path("organisasjonsnummer").asText())
                }
            }
        }
}
