package br.sidia.painel.pessoas

import br.sidia.painel.TestPaths
import br.sidia.painel.security.RoleDirectory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * As mensagens de erro de carga de `equipes.yaml`, `ausencias.yaml` e `planejamento.yaml` vão inteiras para a falha de
 * subida (log): citam o arquivo e o item (#N), mas NUNCA um valor lido (id da pessoa, datas, números do arquivo).
 */
class ErrosSemValorLidoTest {
    private val papeis = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml"))

    private fun arquivo(conteudo: String): Path = Files.createTempFile("erro", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private fun mensagem(bloco: () -> Unit): String = assertFailsWith<IllegalArgumentException> { bloco() }.message.orEmpty()

    private val modelo = Files.readString(TestPaths.configDir.resolve("planejamento.exemplo.yaml"))

    @Test
    fun ausenciasNaoCitamValoresLidos() {
        val casos = listOf(
            "ausencias:\n  - pessoa: dev.alfa1\n    de: 2031-07-09\n    ate: 2031-07-02\n",
            "ausencias:\n  - pessoa: nome.inventado\n    de: 2031-07-02\n    ate: 2031-07-09\n",
            "ausencias:\n  - pessoa: dev.alfa1\n    de: 2031-07-02\n    ate: 2031-07-09\n    fracao: 0.7777\n    motivo: ferias\n",
            "ausencias:\n  - pessoa: dev.alfa1\n    de: 31-07-2031\n    ate: 2031-07-09\n",
            "ausencias:\n  - pessoa: dev.alfa1\n    de: 2031-07-02\n    ate: 2031-07-09\n    fracao: 7.3333\n",
            "ausencias:\n  - pessoa: dev.alfa1\n    de: 2031-07-02\n    ate: 2031-07-09\n  - pessoa: dev.alfa1\n    de: 2031-07-05\n    ate: 2031-07-12\n",
        )
        for (c in casos) {
            val m = mensagem { Ausencias.fromYaml(arquivo(c), papeis) }
            for (valor in listOf("dev.alfa1", "nome.inventado", "2031", "07-09", "31-07", "0.7777", "7.3333", "ferias")) {
                assertTrue(valor !in m, "'$valor' vazou em: $m\n$c")
            }
            assertTrue("#" in m || "sobrep" in m, "sem localização do item: $m")
        }
    }

    @Test
    fun equipesNaoCitamValoresLidos() {
        val casos = listOf(
            "membros:\n  - id: nome.inventado\n    horasSemanais: 40\n    alocacao: 1.0\n",
            "membros:\n  - id: dev.alfa1\n    horasSemanais: 61.5\n    alocacao: 1.0\n",
            "membros:\n  - id: dev.alfa1\n    horasSemanais: 40\n    foco: 1.8888\n    alocacao: 1.0\n",
            "membros:\n  - id: dev.alfa1\n    horasSemanais: 40\n    alocacao: 1.0\n    competencias: [Segredo_X]\n",
            "membros:\n  - id: dev.alfa1\n    horasSemanais: 40\n    alocacao: 1.0\n  - id: dev.alfa1\n    horasSemanais: 40\n    alocacao: 1.0\n",
        )
        for (c in casos) {
            val m = mensagem { Equipes.fromYaml(arquivo(c), papeis) }
            for (valor in listOf("dev.alfa1", "nome.inventado", "61.5", "1.8888", "Segredo_X")) {
                assertTrue(valor !in m, "'$valor' vazou em: $m\n$c")
            }
        }
    }

    @Test
    fun planejamentoNaoCitaValoresLidos() {
        val casos = listOf(
            modelo.replace("focoPadrao: 0.7", "focoPadrao: 1.2345"),
            modelo.replace("horasPadrao: 6.0", "horasPadrao: -7.7777"),
            modelo.replace("ocupacaoAlta: 1.10", "ocupacaoAlta: 0.8123"),
            modelo.replace("Checklist: 6.0", "Checklist: -3.4567"),
        )
        for (c in casos) {
            val m = mensagem { RegrasDePlanejamento.fromYaml(arquivo(c)) }
            for (valor in listOf("1.2345", "7.7777", "0.8123", "3.4567")) assertTrue(valor !in m, "'$valor' vazou em: $m")
        }
    }
}
