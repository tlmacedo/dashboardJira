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
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kaml)
    implementation(libs.logback.classic)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.micrometer.registry.prometheus)

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

// Só os scripts do pacote instalado (bin/painel e bin/painel.bat) fixam o perfil de produção: o pacote sem arquivo de
// variáveis sobe com o modo de desenvolvimento DESLIGADO. `./gradlew run` e `iniciar.*` seguem em modo dev
// (o `run` usa applicationDefaultJvmArgs, que não recebe esta propriedade).
tasks.startScripts {
    defaultJvmOpts = listOf("-Dpainel.perfil=producao")
}

tasks.test {
    // A carga na escala da spec (tag "carga") tem tarefa própria: `testCarga`. A suíte padrão não a executa.
    useJUnitPlatform { excludeTags("carga") }
    // O teste do pacote instalado precisa do resultado de installDist.
    dependsOn(tasks.installDist)
    systemProperty("painel.installDir", layout.buildDirectory.dir("install/painel").get().asFile.absolutePath)
    // Isolamento: a suíte nunca herda a flag do Jira ligada no terminal (testes usam MockEngine, sem rede).
    environment("JIRA_LIVE_ENABLED", "false")
    // Pasta dos YAML de exemplo, por caminho absoluto (não depende do diretório de trabalho).
    systemProperty("painel.configDir", rootProject.layout.projectDirectory.dir("../config").asFile.canonicalPath)
    // Raiz do repositório, por caminho absoluto: testes que leem o workflow de CI e a documentação.
    val raizDoRepo = rootProject.layout.projectDirectory.dir("../..").asFile
    systemProperty("painel.repoDir", raizDoRepo.canonicalPath)
    // Esses arquivos são entrada dos testes: mudar um deles refaz os testes (senão o Gradle os dá por atualizados).
    inputs.files(
        fileTree(raizDoRepo) {
            include(
                ".github/**", "scripts/ci/**", "docs/operacao/**", "docs/jira/**", "docs/implantacao.md", ".gitignore",
                "app/.env.example", "app/iniciar.*", "app/README.md", "app/scripts/**", "app/config/**", "ops/**",
            )
        },
    ).withPropertyName("arquivosDoRepositorioLidosPelosTestes")
    // Fontes de produção, por caminho absoluto: usado pelo teste que varre quem constrói o cliente Jira.
    systemProperty("painel.mainSrcDir", rootProject.layout.projectDirectory.dir("src/main").asFile.canonicalPath)
}

// Desempenho na escala da spec (30.000 issues, 60 pessoas, 50.000 worklogs) sobre o SQLite em arquivo: só os testes com a
// tag "carga", com a memória limitada a 512 MB. Tamanho: `-PescalaDeCarga=completa` (padrão), `moderada` ou `minima`.
// Os números medidos ficam em build/reports/carga/. As variáveis e propriedades da tarefa `test` não são herdadas, então se repetem.
tasks.register<Test>("testCarga") {
    description = "Mede o desempenho na escala da spec sobre o SQLite em arquivo (testes com a tag carga)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("carga") }
    maxHeapSize = "512m"
    environment("JIRA_LIVE_ENABLED", "false")
    systemProperty("painel.configDir", rootProject.layout.projectDirectory.dir("../config").asFile.canonicalPath)
    systemProperty("painel.repoDir", rootProject.layout.projectDirectory.dir("../..").asFile.canonicalPath)
    systemProperty("painel.mainSrcDir", rootProject.layout.projectDirectory.dir("src/main").asFile.canonicalPath)
    systemProperty("painel.installDir", layout.buildDirectory.dir("install/painel").get().asFile.absolutePath)
    (findProperty("escalaDeCarga") as String?)?.let { systemProperty("painel.carga.escala", it) }
    // Segundos do cache de leitura durante a medição (padrão 60, como no painel; 0 mede sem o cache).
    (findProperty("cacheDeCarga") as String?)?.let { systemProperty("painel.carga.cacheSeg", it) }
    // Mostra no terminal o que o teste imprime (as linhas "CARGA ...").
    testLogging { showStandardStreams = true }
    // Sempre roda (o resultado depende da máquina, não só do código).
    outputs.upToDateWhen { false }
    shouldRunAfter(tasks.test)
}
