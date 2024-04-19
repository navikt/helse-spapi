package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal abstract class Konsument(
    internal val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val id: String,
    internal val scope: String,
    internal val behandlingsnummer: String ,
    internal val behandlingsgrunnlag: Behandlingsgrunnlag
) {
    override fun toString() = navn
    open suspend fun request(requestBody: JsonNode): KonsumentRequest = RequiredOrganisasjonsnummerOptionalMinimumSykdomsgrad(requestBody)

    internal companion object {
        private val objectMapper = jacksonObjectMapper()

        private val String.innholdFraResource get() = object {}.javaClass.getResource(this)?.readText() ?: error("Fant ikke resource <$this>")

        private val AlleKonsumenter = setOf(
            FellesordningenForAfp,
            KommunalLandspensjonskasse,
            StatensPensjonskasse,
            StorebrandPensjonstjenester,
            StorebrandLivsforsikring,
            OsloPensjonsforsikring,
            GablerPensjonstjenester,
            Aksio
        )

        private val Map<String, String>.naisFil get() = objectMapper.readTree("/$miljø-nais.json".innholdFraResource)

        internal val Map<String, String>.fellesApi get() = naisFil.path("fellesApi").asBoolean()

        internal val Map<String, String>.konsumenter get() = naisFil
            .path("consumers")
            .associate { Organisasjonsnummer(it.path("orgno").asText()) to "nav:sykepenger:${it.path("scope").asText()}" }
            .map { (organisasjonsnummer, scope) ->
                AlleKonsumenter.singleOrNull { it.organisasjonsnummer == organisasjonsnummer && it.scope == scope } ?: error("Fant ikke konsument med orgnr $organisasjonsnummer og scope $scope")
            }
    }
}

internal object FellesordningenForAfp: Konsument(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    id = "fellesordningen-for-afp",
    scope = "nav:sykepenger:fellesordningenforafp.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object OsloPensjonsforsikring: Konsument(
    navn = "Oslo pensjonsforsikring",
    organisasjonsnummer = Organisasjonsnummer("982759412"),
    id = "oslo-pensjonsforsikring",
    scope = "nav:sykepenger:oslopensjonsforsikring.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StatensPensjonskasse: Konsument(
    navn = "Statens pensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("982583462"),
    id = "statens-pensjonskasse",
    scope = "nav:sykepenger:statenspensjonskasse.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StorebrandLivsforsikring: Konsument(
    navn = "Storebrand livsforsikring",
    organisasjonsnummer = Organisasjonsnummer("958995369"),
    id = "storebrand-livsforsikring",
    scope = "nav:sykepenger:storebrandlivsforsikring.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object KommunalLandspensjonskasse: Konsument(
    navn = "Kommunal landspensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("938708606"),
    id = "kommunal-landspensjonskasse",
    scope = "nav:sykepenger:kommunallandspensjonskasse.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StorebrandPensjonstjenester: Konsument(
    navn = "Storebrand pensjonstjenester",
    organisasjonsnummer = Organisasjonsnummer("931936492"),
    id = "storebrand-pensjonstjenester",
    scope = "nav:sykepenger:storebrandpensjonstjenester.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object GablerPensjonstjenester: Konsument(
    navn = "Gabler pensjonstjenester",
    organisasjonsnummer = Organisasjonsnummer("916833520"),
    id = "gabler-pensjonstjenester",
    scope = "nav:sykepenger:gablerpensjonstjenester.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object Aksio: Konsument(
    navn = "Aksio",
    organisasjonsnummer = Organisasjonsnummer("927613298"),
    id = "aksio",
    scope = "nav:sykepenger:aksio.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)