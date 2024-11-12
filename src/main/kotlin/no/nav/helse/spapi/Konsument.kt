package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import org.intellij.lang.annotations.Language
import java.time.LocalDate

internal sealed class Konsument(
    internal val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val behandlingsnummer: String ,
    internal val behandlingsgrunnlag: Behandlingsgrunnlag
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
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
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

internal abstract class AvtalefestetPensjon(navn: String, organisasjonsnummer: Organisasjonsnummer): Konsument(
    navn = navn,
    organisasjonsnummer = organisasjonsnummer,
    behandlingsnummer = "B709",
    behandlingsgrunnlag = Behandlingsgrunnlag("GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
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

internal object OsloPensjonsforsikring: AvtalefestetPensjon(navn = "Oslo pensjonsforsikring", organisasjonsnummer = Organisasjonsnummer("982759412")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object StatensPensjonskasse: AvtalefestetPensjon(navn = "Statens pensjonskasse", organisasjonsnummer = Organisasjonsnummer("982583462")) { override fun saksId(requestBody: JsonNode) = requestBody.optionalSaksId }
internal object StorebrandLivsforsikring: AvtalefestetPensjon(navn = "Storebrand livsforsikring", organisasjonsnummer = Organisasjonsnummer("958995369")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object KommunalLandspensjonskasse: AvtalefestetPensjon(navn = "Kommunal landspensjonskasse", organisasjonsnummer = Organisasjonsnummer("938708606")) { override fun saksId(requestBody: JsonNode) = requestBody.optionalSaksId }
internal object StorebrandPensjonstjenester: AvtalefestetPensjon(navn = "Storebrand pensjonstjenester", organisasjonsnummer = Organisasjonsnummer("931936492")) { override fun saksId(requestBody: JsonNode) = requestBody.optionalSaksId }
internal object GablerPensjonstjenester: AvtalefestetPensjon(navn = "Gabler pensjonstjenester", organisasjonsnummer = Organisasjonsnummer("916833520")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object ArendalKommunalePensjonskasse: AvtalefestetPensjon(navn = "Arendal kommunale pensjonskasse", organisasjonsnummer = Organisasjonsnummer("940380014")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }
internal object DrammenKommunalePensjonskasse: AvtalefestetPensjon(navn = "Drammen kommunale pensjonskasse", organisasjonsnummer = Organisasjonsnummer("980650383")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }

//  Litt tøysete konsument som bare har tilgang i DEV for å teste selv
internal object Nav: AvtalefestetPensjon(navn = "NAV", organisasjonsnummer = Organisasjonsnummer("889640782")) { override fun saksId(requestBody: JsonNode) = requestBody.requiredSaksId }

// Hvilke konsumenter som registreres når appen starter styres fra dev-nais.json & prod-nais.json i resouces.
internal val AlleKonsumenter = setOf(FellesordningenForAfp, KommunalLandspensjonskasse, StatensPensjonskasse, StorebrandPensjonstjenester, StorebrandLivsforsikring, OsloPensjonsforsikring, GablerPensjonstjenester, ArendalKommunalePensjonskasse, DrammenKommunalePensjonskasse, Nav)