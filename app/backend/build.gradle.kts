import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "br.sidia"

// Versão do pacote (Plano 13): `-Pversao=AAAA.MM.N` (mês 01 a 12, N >= 1); sem a propriedade, `0.0.0-dev` (build local).
// Qualquer outro formato falha o build. `-Pcommit=<curto>` (padrão: git rev-parse --short=7 HEAD; sem git, "desconhecido").
// `-PconstruidaEm=<ISO-8601 UTC>` fixa a data (o script de empacotamento a passa, para o zip ser reproduzível); em build de
// desenvolvimento a data fica vazia, para não invalidar o cache do Gradle a cada execução.
val formatoDaVersao = Regex("""^\d{4}\.(0[1-9]|1[0-2])\.[1-9]\d*$""")
val versaoDoPacote: String = (findProperty("versao") as String?)?.trim()?.takeIf { it.isNotEmpty() }?.also {
    require(formatoDaVersao.matches(it)) { "Versão inválida: esperado AAAA.MM.N (mês 01 a 12, N >= 1), por exemplo -Pversao=2026.11.1" }
} ?: "0.0.0-dev"
val commitDoPacote: String = (findProperty("commit") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: try {
    val p = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD").redirectErrorStream(true).start()
    val saida = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0 && saida.matches(Regex("[0-9a-f]{7,40}"))) saida else "desconhecido"
} catch (e: java.io.IOException) {
    "desconhecido"
}
val construidaEmDoPacote: String = (findProperty("construidaEm") as String?)?.trim()?.takeIf { it.isNotEmpty() }
    ?: if (versaoDoPacote == "0.0.0-dev") "" else Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
version = versaoDoPacote

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
    // UTF-8 em qualquer sistema (no Windows o padrão da JVM de 21 já é UTF-8, mas o Java de 17 para trás usava ANSI).
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

// `./gradlew run` roda a partir de app/ para que config/, frontend/dist, data/ e o arquivo de variáveis (caminhos relativos) existam.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.layout.projectDirectory.dir("..").asFile
}

// Só os scripts do pacote instalado (bin/painel e bin/painel.bat) fixam o perfil de produção: o pacote sem arquivo de
// variáveis sobe com o modo de desenvolvimento DESLIGADO. `./gradlew run` e `iniciar.*` seguem em modo dev
// (o `run` usa applicationDefaultJvmArgs, que não recebe esta propriedade).
tasks.startScripts {
    defaultJvmOpts = listOf("-Dpainel.perfil=producao", "-Dfile.encoding=UTF-8")
}

// Grava versao, commit e construidaEm em build/generated/versao/versao.properties, que entra nos recursos do main
// (lido por VersaoDoPacote). Fica no jar, no VERSAO.txt do pacote e no log da subida; nunca em rota pública.
val gerarVersaoProperties = tasks.register("gerarVersaoProperties") {
    val saida = layout.buildDirectory.file("generated/versao/versao.properties")
    val v = versaoDoPacote
    val c = commitDoPacote
    val q = construidaEmDoPacote
    inputs.property("versao", v)
    inputs.property("commit", c)
    inputs.property("construidaEm", q)
    outputs.file(saida)
    doLast {
        val arquivo = saida.get().asFile
        arquivo.parentFile.mkdirs()
        arquivo.writeText("versao=$v\ncommit=$c\nconstruidaEm=$q\n")
    }
}
sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/versao"))
}
tasks.processResources { dependsOn(gerarVersaoProperties) }

// Zip reprodutível do pacote de produção (Plano 13, T6): `painel-<versao>.zip`, com a pasta raiz `painel-<versao>/`, a partir
// de app/pacote/painel, que o script `montar-pacote` já montou e filtrou (só modelos de configuração, nunca arquivo real).
// Mesma entrada e mesmo -PconstruidaEm = mesmo SHA-256. Use pelo script: `scripts/montar-pacote.* <versao>`.
tasks.register<Zip>("montarPacote") {
    group = "distribution"
    description = "Gera app/pacote/painel-<versao>.zip a partir de app/pacote/painel (exige -Pversao=AAAA.MM.N)."
    val pastaDoPacote = rootProject.layout.projectDirectory.dir("../pacote/painel")
    archiveFileName.set("painel-$versaoDoPacote.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("../pacote"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    filePermissions { unix("rw-r--r--") }
    dirPermissions { unix("rwxr-xr-x") }
    from(pastaDoPacote) {
        into("painel-$versaoDoPacote")
        filesMatching(listOf("bin/painel", "**/*.sh")) { permissions { unix("rwxr-xr-x") } }
    }
    doFirst {
        if (versaoDoPacote == "0.0.0-dev") throw GradleException("montarPacote exige -Pversao=AAAA.MM.N")
        val pasta = pastaDoPacote.asFile
        if (!pasta.resolve("frontend/dist/index.html").isFile) throw GradleException("falta frontend/dist/index.html em ${pasta.name}: monte o pacote com scripts/montar-pacote")
        if (!pasta.resolve("bin/painel").isFile) throw GradleException("falta bin/painel em ${pasta.name}: monte o pacote com scripts/montar-pacote")
    }
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
                ".github/**", "scripts/ci/**", "docs/operacao/**", "docs/jira/**", "docs/implantacao.md", "docs/implantacao/**", "deploy/**", ".gitignore",
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
