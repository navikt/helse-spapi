package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.ZoneId

internal sealed class Konsument(
    internal val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val behandlingsnummer: String ,
    internal val behandlingsgrunnlag: Behandlingsgrunnlag,
    internal val integrator: Organisasjonsnummer? = null
) {
    override fun toString() = navn

    // Per nå er det ingen konsumenter som har forskjellige versjoner, men det er nok bare et spørsmål om tid
    abstract suspend fun request(requestBody: JsonNode, versjon: Int): KonsumentRequest
}

internal sealed class AfpRequest(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val personidentifikator: Personidentifikator,
    private val organisasjonsnummer: Organisasjonsnummer,
    private val minimumSykdomsgrad: Int?
): KonsumentRequest {

    override fun filtrer(utbetaltePerioder: List<UtbetaltPeriode>) = utbetaltePerioder
        .filter { it.organisasjonsnummer == organisasjonsnummer }
        .filter { it.grad >= (minimumSykdomsgrad ?: 0) }

    @Language("JSON")
    override fun json(utbetaltPeriode: UtbetaltPeriode) = """
        {
          "fraOgMedDato": "${utbetaltPeriode.fom}",
          "tilOgMedDato": "${utbetaltPeriode.tom}",
          "tags": ${utbetaltPeriode.tags.map { "\"$it\"" }}
          ${if (minimumSykdomsgrad == null) ",\"sykdomsgrad\": ${utbetaltPeriode.grad}" else ""}
        }
    """
}

internal object FellesordningenForAfp: Konsument(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, jf. §29 andre ledd, første punktum. GDPR Art. 9(2)b")
) {
    override suspend fun request(requestBody: JsonNode, versjon: Int) = FellesordningenForAfpRequest(requestBody)

    internal class FellesordningenForAfpRequest(requestBody: JsonNode): AfpRequest(
        fom = requestBody.periode.first,
        tom = requestBody.periode.second,
        personidentifikator = requestBody.personidentifikator,
        organisasjonsnummer = requestBody.organisasjonsnummer,
        minimumSykdomsgrad = requestBody.optionalMinimumSykdomsgrad
    )
}

internal abstract class AvtalefestetPensjon(
    navn: String,
    organisasjonsnummer: Organisasjonsnummer,
    integrator: Organisasjonsnummer? = null,
    behandlingsgrunnlag: Behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. Forsikringsvirksomhetsloven § 4-17 første ledd. GDPR Art. 9(2)b"),
) : Konsument(
    navn = navn,
    organisasjonsnummer = organisasjonsnummer,
    behandlingsnummer = "B709",
    behandlingsgrunnlag = behandlingsgrunnlag,
    integrator = integrator
) {
    override suspend fun request(requestBody: JsonNode, versjon: Int) = AvtalefestetPensjonRequest(requestBody, saksId(requestBody))

    abstract fun saksId(requestBody: JsonNode): SaksId?

    internal class AvtalefestetPensjonRequest(requestBody: JsonNode, private val saksId: SaksId?): AfpRequest(
        fom = requestBody.periode.first,
        tom = requestBody.periode.second,
        personidentifikator = requestBody.personidentifikator,
        organisasjonsnummer = requestBody.organisasjonsnummer,
        minimumSykdomsgrad = requestBody.optionalMinimumSykdomsgrad
    ) {
        override fun berik(response: ObjectNode): ObjectNode {
            saksId?.let { id -> response.put("saksId", "$id") }
            return response
        }
    }
}

internal object StatensPensjonskasse : AvtalefestetPensjon(
    navn = "Statens pensjonskasse",
    organisasjonsnummer = Organisasjonsnummer("982583462"),
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. Lov om AFP for medlemmer av Statens pensjonskasse §13 andre ledd. GDPR Art. 9(2)b")
) {
    private val KrevSaksIdFraOgMed = LocalDate.parse("2025-02-01")
    private fun nå() = LocalDate.now(ZoneId.of("Europe/Oslo"))
    override fun saksId(requestBody: JsonNode): SaksId? {
        return if (nå() >= KrevSaksIdFraOgMed) requestBody.requiredSaksId
        else requestBody.optionalSaksId
    }
}
internal object OsloPensjonsforsikring: AvtalefestetPensjon(navn = "Oslo pensjonsforsikring", organisasjonsnummer = Organisasjonsnummer("982759412")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object StorebrandLivsforsikring: AvtalefestetPensjon(navn = "Storebrand livsforsikring", organisasjonsnummer = Organisasjonsnummer("958995369")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object KommunalLandspensjonskasse: AvtalefestetPensjon(navn = "Kommunal landspensjonskasse", organisasjonsnummer = Organisasjonsnummer("938708606")) {
    private val prod = System.getenv().miljø == "prod"
    override fun saksId(requestBody: JsonNode): SaksId? {
        return if (prod) requestBody.requiredSaksId
        else requestBody.optionalSaksId
    }
}
// Disse to er også integratorer (systemleverandører) slik som Aksio, men bruker ikke delegert tokens i maskinporten
// Så de fremstår som pensjonskasser for oss på tross av at de ikke er det
internal object StorebrandPensjonstjenester: AvtalefestetPensjon(navn = "Storebrand pensjonstjenester", organisasjonsnummer = Organisasjonsnummer("931936492")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object GablerPensjonstjenester: AvtalefestetPensjon(navn = "Gabler pensjonstjenester", organisasjonsnummer = Organisasjonsnummer("916833520")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }

private val Aksio = Organisasjonsnummer("927613298")
internal object ArendalKommunalePensjonskasse: AvtalefestetPensjon(navn = "Arendal kommunale pensjonskasse", organisasjonsnummer = Organisasjonsnummer("940380014"), integrator = Aksio) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object DrammenKommunalePensjonskasse: AvtalefestetPensjon(navn = "Drammen kommunale pensjonskasse", organisasjonsnummer = Organisasjonsnummer("980650383"), integrator = Aksio) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }

//  Litt tøysete konsument som bare har tilgang i DEV for å teste selv
internal object Nav: AvtalefestetPensjon(navn = "NAV", organisasjonsnummer = Organisasjonsnummer("889640782")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }

// Hvilke konsumenter som registreres når appen starter styres fra dev-nais.json & prod-nais.json i resouces.
internal val AlleKonsumenter = setOf(FellesordningenForAfp, KommunalLandspensjonskasse, StatensPensjonskasse, StorebrandPensjonstjenester, StorebrandLivsforsikring, OsloPensjonsforsikring, GablerPensjonstjenester, ArendalKommunalePensjonskasse, DrammenKommunalePensjonskasse, Nav)