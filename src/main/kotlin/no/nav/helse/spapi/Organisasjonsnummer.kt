package no.nav.helse.spapi

internal data class Organisasjonsnummer(private val id: String) {
    init { check(id.matches(regex)) { "Ugyldig organisasjonsnummer $id" } }

    override fun toString() = id

    private companion object {
        val regex = "\\d{9}".toRegex()
    }
}