package no.nav.helse.spapi

internal open class Konsument(
    private val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val id: String,
    internal val scope: String
) {
    override fun toString() = navn
    internal val endepunkt = "/$id"
}

internal object FellesordningenForAfp: Konsument(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    id = "fellesordningen-for-afp",
    scope = "nav:sykepenger:fellesordningenforafp.read"
)
