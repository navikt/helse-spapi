package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode

internal abstract class Konsument(
    internal val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val behandlingsnummer: String ,
    internal val behandlingsgrunnlag: Behandlingsgrunnlag
) {
    override fun toString() = navn
    open suspend fun request(requestBody: JsonNode): KonsumentRequest = RequiredOrganisasjonsnummerOptionalMinimumSykdomsgrad(requestBody)

    internal companion object {
        internal val AlleKonsumenter = setOf(
            FellesordningenForAfp,
            KommunalLandspensjonskasse,
            StatensPensjonskasse,
            StorebrandPensjonstjenester,
            StorebrandLivsforsikring,
            OsloPensjonsforsikring,
            GablerPensjonstjenester,
            Aksio
        )
    }
}

internal object FellesordningenForAfp: Konsument(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object OsloPensjonsforsikring: Konsument(
    navn = "Oslo pensjonsforsikring",
    organisasjonsnummer = Organisasjonsnummer("982759412"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StatensPensjonskasse: Konsument(
    navn = "Statens pensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("982583462"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StorebrandLivsforsikring: Konsument(
    navn = "Storebrand livsforsikring",
    organisasjonsnummer = Organisasjonsnummer("958995369"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object KommunalLandspensjonskasse: Konsument(
    navn = "Kommunal landspensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("938708606"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object StorebrandPensjonstjenester: Konsument(
    navn = "Storebrand pensjonstjenester",
    organisasjonsnummer = Organisasjonsnummer("931936492"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object GablerPensjonstjenester: Konsument(
    navn = "Gabler pensjonstjenester",
    organisasjonsnummer = Organisasjonsnummer("916833520"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)
internal object Aksio: Konsument(
    navn = "Aksio",
    organisasjonsnummer = Organisasjonsnummer("927613298"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
)