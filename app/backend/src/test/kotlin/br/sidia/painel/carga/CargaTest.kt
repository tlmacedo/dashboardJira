package br.sidia.painel.carga

import org.junit.jupiter.api.Tag
import java.time.Duration

/**
 * Desempenho na ESCALA DA SPEC (30.000 issues, 60 pessoas, 50.000 worklogs) sobre o SQLite em arquivo. Fora da suíte
 * padrão (tag `carga`): rode `./gradlew testCarga` (Windows: `.\gradlew.bat testCarga`). Com `-Dpainel.carga.escala=moderada`
 * (propriedade do Gradle `-PescalaDeCarga=moderada`) roda o cenário menor, para máquinas mais lentas e para o CI.
 */
@Tag("carga")
class CargaTest : CenarioDeCarga(
    escala = Escala.daPropriedade(),
    duracaoDaLeitura = Duration.ofSeconds(20),
    issuesDaSincronizacao = 2_000,
)
