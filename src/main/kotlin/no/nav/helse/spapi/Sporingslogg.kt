package no.nav.helse.spapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

internal class Sporingslogg {
    internal fun logg(person : Personidentifikator, konsument: Konsument, leverteData: String) {
        val entry = objectMapper.createObjectNode().apply {
            put("person", "$person")
            put("mottaker", "${konsument.organisasjonsnummer}")
            put("tema", "SYK")
            put("behandlingsGrunnlag", "GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
            put("uthentingsTidspunkt", "${LocalDateTime.now()}")
            put("leverteData", Base64.getEncoder().encodeToString(leverteData.encodeToByteArray()))
        }
        // TODO: Noe Kafka-greier
        sikkerlogg.info("Sender data til $konsument for personen $person:\n\tJSON: $leverteData\n\tSporingslogg: $entry")
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
    }
}