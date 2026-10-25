package br.sidia.painel

import java.nio.file.Path

/** Caminhos de apoio aos testes, independentes do diretório de trabalho do Gradle. */
object TestPaths {
    /** Pasta `app/config`, definida em `build.gradle.kts` pela propriedade `painel.configDir`. */
    val configDir: Path = Path.of(
        System.getProperty("painel.configDir")
            ?: error("Propriedade painel.configDir ausente: rode os testes pelo Gradle"),
    )
}
