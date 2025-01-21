package no.nav.helse.spapi

internal data class SaksId(private val id: String) {
    init { check(id.matches(regex)) { "Ugyldig saksnummer $id" } }

    override fun toString() = id

    private companion object {
        val regex = """[a-zæøåA-ZÆØÅ0-9-_:.]{1,200}""".toRegex()
    }
}
