plugins {
    alias(libs.plugins.sykepenger.deployable)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.spapi.AppKt"
}

dependencies {
    implementation(libs.tbd.libs.azure.token.client.default)
    implementation(libs.tbd.libs.naisful.app)
    implementation(libs.tbd.libs.retry)

    implementation(libs.bundles.logback)

    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)

    implementation(libs.kafka.clients)

    testImplementation(libs.jsonassert)
    testImplementation(libs.handlebars)
    testImplementation(libs.jackson.dataformat.yaml)
    testImplementation(libs.tbd.libs.naisful.test.app)
    testImplementation(libs.tbd.libs.signed.jwt.issuer.test) {
        // Standard-wiremock kjører på Jetty 11, som ikke finnes i Jetty-BOM-en fra no.nav.sykepenger.kotlin
        exclude(group = "org.wiremock", module = "wiremock")
    }
    testImplementation(libs.wiremock)
}
