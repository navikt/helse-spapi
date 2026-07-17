plugins {
    alias(libs.plugins.kotlin.jvm)
}

val mainClass = "no.nav.helse.spapi.AppKt"

dependencies {
    implementation(libs.tbd.libs.azure.token.client.default)
    implementation(libs.tbd.libs.naisful.app)
    implementation(libs.tbd.libs.retry)

    implementation(libs.bundles.logback)

    implementation(platform(libs.ktor.bom))
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)

    implementation(libs.kafka.clients)
    implementation(platform(libs.jackson2.bom))
    implementation(platform(libs.jackson3.bom))

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jsonassert)
    testImplementation(libs.handlebars)
    testImplementation(libs.tbd.libs.naisful.test.app)
    testImplementation(libs.tbd.libs.signed.jwt.issuer.test)
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
