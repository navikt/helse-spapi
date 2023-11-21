package no.nav.helse.spapi

internal open class Konsument(
    private val navn: String,
    internal val organisasjonsnummer: Organisasjonsnummer,
    internal val id: String,
    internal val scope: String,
    internal val behandlingsnummer: String,
    internal val behandlingsgrunnlag: String
) {
    init {
        val escaped = behandlingsgrunnlag.replace("(", "\\(").replace(")", "\\)")
        check(escaped.length <= 100) {
            "behandlingsgrunnlaget '$behandlingsgrunnlag' er for langt. Er ${escaped.length} tegn!!!!"
        }
    }
    override fun toString() = navn
    internal val endepunkt = "/$id"
}

internal object FellesordningenForAfp: Konsument(
    navn = "Fellesordningen for AFP",
    organisasjonsnummer = Organisasjonsnummer("987414502"),
    id = "fellesordningen-for-afp",
    scope = "nav:sykepenger:fellesordningenforafp.read",
    behandlingsnummer = "B709",
    behandlingsgrunnlag = "GDPR Art. 6(1)e, 9(2)b. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum"
)

