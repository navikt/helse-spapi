package no.nav.helse.spapi.utbetalteperioder

import java.time.LocalDate

internal class UtbetaltPeriode(
    internal val fom: LocalDate,
    internal val tom: LocalDate,
    internal val arbeidsgiver: String,
    internal val grad: Int
)
