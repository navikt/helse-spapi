package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import no.nav.helse.spapi.utbetalteperioder.UtbetaltPeriode
import java.lang.IllegalArgumentException
import java.time.LocalDate

internal interface KonsumentRequest {
    val fom: LocalDate
    val tom: LocalDate
    val personidentifikator: Personidentifikator
    fun filtrer(utbetaltePerioder: List<UtbetaltPeriode>): List<UtbetaltPeriode>
    fun json(utbetaltPeriode: UtbetaltPeriode): String
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
internal val JsonNode.personidentifikator get() = required("personidentifikator") { Personidentifikator(it.asText()) }
internal val JsonNode.organisasjonsnummer get() = required("organisasjonsnummer") { Organisasjonsnummer(it.asText()) }
internal val JsonNode.periode get(): Pair<LocalDate, LocalDate> {
    val fom = required("fraOgMedDato") { LocalDate.parse(it.asText()) }
    val tom = required("tilOgMedDato") { LocalDate.parse(it.asText()).also { tom -> check(fom <= tom) { "Ugyldig periode $fom til $tom" } } }
    return fom to tom
}
internal val JsonNode.optionalMinimumSykdomsgrad get() = optional("minimumSykdomsgrad") {
    it.asInt().also { minimumSykdomsgrad -> check(minimumSykdomsgrad in 1..100) { "Må være mellom 1 og 100" } }
}
