package br.sidia.painel.implantacao

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.fail

/**
 * Provas negativas reais dos scripts `sh` precisam de `sh`, `unzip`, `sha256sum` e `openssl`. No CI Linux (`CI=true`) faltar uma
 * ferramenta FALHA o teste (senão a prova viraria "skipped" em silêncio); na máquina do desenvolvedor, ou em Windows, pula.
 */
object Ferramentas {
    fun exigir(ok: Boolean, motivo: String) {
        val ci = System.getenv("CI") == "true"
        val linux = System.getProperty("os.name").lowercase().contains("linux")
        if (!ok && ci && linux) fail("CI Linux sem ferramenta necessária: $motivo")
        assumeTrue(ok, motivo)
    }
}
