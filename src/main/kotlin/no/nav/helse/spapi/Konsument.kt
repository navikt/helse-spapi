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
    abstract suspend fun request(requestBody: JsonNode, versjon: Int): KonsumentRequest

    internal companion object {
        internal val AlleKonsumenter = setOf(
            FellesordningenForAfp,
            KommunalLandspensjonskasse,
            StatensPensjonskasse,
            StorebrandPensjonstjenester,
            StorebrandLivsforsikring,
            OsloPensjonsforsikring,
            GablerPensjonstjenester,
            Aksio,
            Nav
        )
    }
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
    override suspend fun request(requestBody: JsonNode, versjon: Int) = FellesordningenForAfpRequest(requestBody) // Fellesrdningen for AFP har ikke fler versjoner :)

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
    override suspend fun request(requestBody: JsonNode, versjon: Int): KonsumentRequest {
        val saksId = when (versjon) {
            1 -> requestBody.optionalSaksId
            2 -> requestBody.requiredSaksId
            else -> throw FinnesIkke("Versjon $versjon finnes ikke for avtalefestet pensjon.")
        }
        return AvtalefestetPensjonRequest(requestBody, saksId)
    }

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

internal object OsloPensjonsforsikring: AvtalefestetPensjon(navn = "Oslo pensjonsforsikring", organisasjonsnummer = Organisasjonsnummer("982759412"))
internal object StatensPensjonskasse: AvtalefestetPensjon(navn = "Statens pensjonskasse", organisasjonsnummer = Organisasjonsnummer("982583462"))
internal object StorebrandLivsforsikring: AvtalefestetPensjon(navn = "Storebrand livsforsikring", organisasjonsnummer = Organisasjonsnummer("958995369"))
internal object KommunalLandspensjonskasse: AvtalefestetPensjon(navn = "Kommunal landspensjonskasse", organisasjonsnummer = Organisasjonsnummer("938708606"))
internal object StorebrandPensjonstjenester: AvtalefestetPensjon(navn = "Storebrand pensjonstjenester", organisasjonsnummer = Organisasjonsnummer("931936492"))
internal object GablerPensjonstjenester: AvtalefestetPensjon(navn = "Gabler pensjonstjenester", organisasjonsnummer = Organisasjonsnummer("916833520"))
internal object Aksio: AvtalefestetPensjon(navn = "Aksio", organisasjonsnummer = Organisasjonsnummer("927613298"))

//  Litt tøysete konsument som bare har tilgang i DEV for å teste selv
internal object Nav: AvtalefestetPensjon(navn = "NAV", organisasjonsnummer = Organisasjonsnummer("889640782"))