package no.nav.helse.spapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spapi.personidentifikator.Personidentifikator
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.*

internal abstract class Sporingslogg {
    internal fun logg(person : Personidentifikator, konsument: Konsument, leverteData: String) {
        val logginnslag = objectMapper.createObjectNode().apply {
            put("person", "$person")
            put("mottaker", "${konsument.organisasjonsnummer}")
            put("tema", "SYK")
            //put("behandlingsGrunnlag", "GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd, første punktum. GDPR Art. 9(2)b")
            put("behandlingsGrunnlag", "GDPR Art. 6(1)e. AFP-tilskottsloven §17 første ledd, §29 andre ledd. GDPR Art. 9(2)b")
            put("uthentingsTidspunkt", "${LocalDateTime.now()}")
            put("leverteData", Base64.getEncoder().encodeToString(leverteData.encodeToByteArray()))
        }
        send(logginnslag)
        sikkerlogg.info("Sender data til $konsument for personen $person:\n\tJSON: $leverteData\n\tSporingslogg: $logginnslag")
    }

    abstract fun send(logginnslag: JsonNode)

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
    }
}

internal class KafkaSporingslogg(config: Map<String, String>): Sporingslogg() {

    private val properties = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.hent("KAFKA_BROKERS"))
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, config.hent("KAFKA_TRUSTSTORE_PATH"))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, config.hent("KAFKA_CREDSTORE_PASSWORD"))
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, config.hent("KAFKA_KEYSTORE_PATH"))
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, config.hent("KAFKA_CREDSTORE_PASSWORD"))
        put(ProducerConfig.CLIENT_ID_CONFIG, InetAddress.getLocalHost().hostName)
        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }

    private val topic = config.hent("SPORINGSLOGG_TOPIC")
    private val producer = KafkaProducer(properties, StringSerializer(), StringSerializer())

    override fun send(logginnslag: JsonNode) {
        producer.send(ProducerRecord(topic, logginnslag.toString())).get()
    }
}