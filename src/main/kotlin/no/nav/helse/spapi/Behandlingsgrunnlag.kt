package no.nav.helse.spapi

internal class Behandlingsgrunnlag(private val behandlingsgrunnlag: String) {
    init {
        val escaped = behandlingsgrunnlag.replace("(", "\\(").replace(")", "\\)")
        check(escaped.length <= 100) {
            "behandlingsgrunnlaget '$behandlingsgrunnlag' er for langt. Er ${escaped.length} tegn!!!!"
        }
    }

    override fun toString() = behandlingsgrunnlag
}