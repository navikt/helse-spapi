package no.nav.helse.spapi

internal class Organisasjonsnummer(private val id: String) {
    init {
        check(id.matches("\\d{9}".toRegex())) { "Ugyldig organisasjonsnummer" }
    }

    override fun toString() = id
    override fun equals(other: Any?) = other is Organisasjonsnummer && other.id == this.id
}