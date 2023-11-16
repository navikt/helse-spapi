package no.nav.helse.spapi

internal class Organisasjonsnummer(private val id: String) {
    init {
        check(id.matches("\\d{9}".toRegex())) { "Ugyldig organisasjonsnummer" }
    }

    override fun toString() = id
}