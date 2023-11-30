package no.nav.helse.spapi.utbetalteperioder

import no.nav.helse.spapi.Organisasjonsnummer
import java.time.LocalDate

internal class UtbetaltPeriode(
    internal val fom: LocalDate,
    internal val tom: LocalDate,
    internal val organisasjonsnummer: Organisasjonsnummer?,
    internal val grad: Int,
    internal val tags: Set<String>
)
