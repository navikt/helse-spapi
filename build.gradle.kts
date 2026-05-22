plugins {
    kotlin("jvm") version "2.3.0"
}

val ktorVersion = "3.2.3"
val tbdLibsVersion = "2026.01.22-09.16-1d3f6039"

val logbackClassicVersion = "1.5.25"
val logbackEncoderVersion = "8.0"
val junitJupiterVersion = "5.12.1"
val kafkaVersion = "4.2.0"
val jsonAssertVersion = "1.5.3"
val handlebarsVersion = "4.5.1"

val mainClass = "no.nav.helse.spapi.AppKt"

dependencies {
    implementation("com.github.navikt.tbd-libs:naisful-app:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")

    implementation("ch.qos.logback:logback-classic:$logbackClassicVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")

    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("com.github.navikt.tbd-libs:retry:$tbdLibsVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.skyscreamer:jsonassert:$jsonAssertVersion")
    testImplementation("com.github.jknack:handlebars:$handlebarsVersion")
    testImplementation("com.github.navikt.tbd-libs:naisful-test-app:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:signed-jwt-issuer-test:$tbdLibsVersion")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("21"))
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    named<Jar>("jar") {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClass
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
}
