plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "br.sidia"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kaml)
    implementation(libs.logback.classic)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.postgresql)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("br.sidia.painel.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    // Pasta dos YAML de exemplo, por caminho absoluto (não depende do diretório de trabalho).
    systemProperty("painel.configDir", rootProject.layout.projectDirectory.dir("../config").asFile.canonicalPath)
    // Fontes de produção, por caminho absoluto: usado pelo teste que varre quem constrói o cliente Jira.
    systemProperty("painel.mainSrcDir", rootProject.layout.projectDirectory.dir("src/main").asFile.canonicalPath)
}
