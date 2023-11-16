package no.nav.helse.spapi

internal class Personidentifikator(private val id: String) {
    init {
        check(id.matches("\\d{11}".toRegex())) { "Ugyldig personidentifikator" }
    }

    override fun toString() = id
}