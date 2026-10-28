package br.sidia.painel

import java.nio.file.Path

/** Caminhos de apoio aos testes, independentes do diretório de trabalho do Gradle. */
object TestPaths {
    /** Pasta `app/config`, definida em `build.gradle.kts` pela propriedade `painel.configDir`. */
    val configDir: Path = Path.of(
        System.getProperty("painel.configDir")
            ?: error("Propriedade painel.configDir ausente: rode os testes pelo Gradle"),
    )

    /** Raiz do repositório (caminho canônico), definida em `build.gradle.kts` pela propriedade `painel.repoDir`. */
    val repoDir: Path = Path.of(
        System.getProperty("painel.repoDir")
            ?: error("Propriedade painel.repoDir ausente: rode os testes pelo Gradle"),
    )

    /** Pasta `app/backend/src/main`, definida em `build.gradle.kts` pela propriedade `painel.mainSrcDir`. */
    val mainSrcDir: Path = Path.of(
        System.getProperty("painel.mainSrcDir")
            ?: error("Propriedade painel.mainSrcDir ausente: rode os testes pelo Gradle"),
    )
}
