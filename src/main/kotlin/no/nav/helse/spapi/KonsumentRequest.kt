package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.server.application.*
import io.ktor.server.request.*
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import org.intellij.lang.annotations.Language
import java.lang.IllegalArgumentException
import java.time.LocalDate

internal interface KonsumentRequest {
    val fom: LocalDate
    val tom: LocalDate
    val personidentifikator: Personidentifikator
    fun filtrer(utbetaltePerioder: List<UtbetaltPeriode>): List<UtbetaltPeriode>
    fun json(utbetaltPeriode: UtbetaltPeriode): String
}

internal class RequiredOrganisasjonsnummerOptionalMinimumSykdomsgrad private constructor(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val personidentifikator: Personidentifikator,
    private val organisasjonsnummer: Organisasjonsnummer,
    private val minimumSykdomsgrad: Int?
): KonsumentRequest {

    internal constructor(requestBody: JsonNode): this(
        fom = requestBody.periode.first,
        tom = requestBody.periode.second,
        personidentifikator = requestBody.personidentifikator,
        organisasjonsnummer = requestBody.organisasjonsnummer,
        minimumSykdomsgrad = requestBody.optionalMinimumSykdomsgrad
    )

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

internal class UgyldigInputException(melding: String, cause: Throwable? = null): IllegalArgumentException(melding, cause)

private fun JsonNode.hent(path: String) = path(path).takeUnless { it.isMissingNode || it.isNull }
private fun <T> JsonNode.required(path: String, transformer: (jsonNode: JsonNode) -> T): T {
    val jsonNode = hent(path) ?: throw UgyldigInputException("Mangler feltet '$path' i request body.")
    return try { transformer(jsonNode) } catch (throwable: Throwable) {
        throw UgyldigInputException("Ugyldig verdi i feltet '$path' i request body. (var ${jsonNode.asText()})")
    }
}
private fun <T> JsonNode.optional(path: String, transformer: (jsonNode: JsonNode) -> T): T? {
    val jsonNode = hent(path) ?: return null
    return try { transformer(jsonNode) } catch (throwable: Throwable) {
        throw UgyldigInputException("Ugyldig verdi i feltet '$path' i request body: ${throwable.message} (verdien var ${jsonNode.asText()})")
    }
}
private val JsonNode.personidentifikator get() = required("personidentifikator") { Personidentifikator(it.asText()) }
private val JsonNode.organisasjonsnummer get() = required("organisasjonsnummer") { Organisasjonsnummer(it.asText()) }
private val JsonNode.periode get(): Pair<LocalDate, LocalDate> {
    val fom = required("fraOgMedDato") { LocalDate.parse(it.asText()) }
    val tom = required("tilOgMedDato") { LocalDate.parse(it.asText()).also { tom -> check(fom <= tom) { "Ugyldig periode $fom til $tom" } } }
    return fom to tom
}
private val JsonNode.optionalMinimumSykdomsgrad get() = optional("minimumSykdomsgrad") {
    it.asInt().also { minimumSykdomsgrad -> check(minimumSykdomsgrad in 1..100) { "Må være mellom 1 og 100" } }
}
