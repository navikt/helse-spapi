package no.nav.helse.spapi.personidentifikator

internal data class Personidentifikator(private val id: String) {
    init { check(id.matches(regex)) { "Ugyldig personidentifikator" } }

    override fun toString() = id

    private companion object {
        val regex = "\\d{11}".toRegex()
    }
}