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
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit5)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("br.sidia.painel.ApplicationKt")
}

// `./gradlew run` roda a partir de app/ para que config/, frontend/dist, data/ e o arquivo de variáveis (caminhos relativos) existam.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.layout.projectDirectory.dir("..").asFile
}

tasks.test {
    useJUnitPlatform()
    // Isolamento: a suíte nunca herda a flag do Jira ligada no terminal (testes usam MockEngine, sem rede).
    environment("JIRA_LIVE_ENABLED", "false")
    // Pasta dos YAML de exemplo, por caminho absoluto (não depende do diretório de trabalho).
    systemProperty("painel.configDir", rootProject.layout.projectDirectory.dir("../config").asFile.canonicalPath)
    // Fontes de produção, por caminho absoluto: usado pelo teste que varre quem constrói o cliente Jira.
    systemProperty("painel.mainSrcDir", rootProject.layout.projectDirectory.dir("src/main").asFile.canonicalPath)
}
