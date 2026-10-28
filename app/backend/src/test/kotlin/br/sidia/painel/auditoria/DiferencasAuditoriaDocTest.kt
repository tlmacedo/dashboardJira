package br.sidia.painel.auditoria

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Protege os documentos da auditoria: as diferenças com o app atual e os itens novos da 1ª conexão. Só leitura de arquivos. */
class DiferencasAuditoriaDocTest {
    private val raiz: Path = TestPaths.configDir.toAbsolutePath().normalize().parent.parent

    private fun ler(relativo: String): String {
        val arquivo = raiz.resolve(relativo)
        assertTrue(Files.isRegularFile(arquivo), "Documento ausente: $arquivo")
        return Files.readString(arquivo)
    }

    private val diferencas = "docs/analise/diferencas-esperadas-auditoria.md"

    @Test
    fun documentoCitaTodosOsCodigos() {
        val texto = ler(diferencas)
        for (codigo in CodigoSinal.entries) {
            assertTrue(texto.contains(codigo.name), "Documento de diferenças sem o código ${codigo.name}")
        }
    }

    @Test
    fun documentoDizOQueFicaDeForaEAsDiferencasDeRegra() {
        val texto = ler(diferencas)
        for (item in listOf("Estimativa reduzida", "Worklog retroativo", "Integridade", "histórico de reporter", "Manaus", "no prazo ajustado", "em lote", "indício, não prova")) {
            assertTrue(texto.contains(item, ignoreCase = true), "Documento de diferenças sem '$item'")
        }
        // Cada sinal tem o limite configurável ou a menção "novo" na sua linha da tabela.
        for (codigo in CodigoSinal.entries) {
            val linha = texto.lines().firstOrNull { it.startsWith("|") && it.contains(codigo.name) }
            assertTrue(linha != null, "Sem linha de tabela para ${codigo.name}")
            assertTrue(linha.contains("novo", ignoreCase = true) || linha.contains("Integridade", ignoreCase = true), "Linha de ${codigo.name} sem o equivalente no app atual")
        }
    }

    @Test
    fun documentoSemDadoPessoalNemHostReal() {
        val texto = ler(diferencas)
        assertTrue(!Regex("https?://").containsMatchIn(texto), "Documento com URL")
        assertTrue(!Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+").containsMatchIn(texto), "Documento com e-mail")
    }

    @Test
    fun checklistDoPasso4ConfereDuedateLabelsEReporter() {
        val texto = ler("docs/jira/checklist-primeira-conexao.md")
        val inicio = texto.indexOf("## Passo 4")
        val fim = texto.indexOf("## Passo 5")
        assertTrue(inicio >= 0 && fim > inicio, "Checklist sem a seção do Passo 4")
        val passo4 = texto.substring(inicio, fim)
        for (item in listOf("fromString", "duedate", "separador", "labels", "reporter")) {
            assertTrue(passo4.contains(item, ignoreCase = true), "Passo 4 sem '$item'")
        }
        assertTrue(passo4.contains("nome do campo"), "Passo 4 sem a explicação de que o reporter só traz o nome do campo")
    }

    @Test
    fun matrizTemAsLinhasDeDuedateELabelsNoChangelog() {
        val texto = ler("docs/jira/matriz-compatibilidade.md")
        for (campo in listOf("duedate", "labels")) {
            val linha = texto.lines().firstOrNull { it.startsWith("|") && it.contains("`$campo`") && it.contains("changelog", ignoreCase = true) }
            assertTrue(linha != null, "Matriz sem a linha de `$campo` no changelog")
            assertTrue(linha.contains("Não validado"), "A linha de `$campo` deve estar como não validada")
        }
    }

    @Test
    fun handoffRegistraAPendenciaDoDpoESemDadoReal() {
        val texto = ler("HANDOFF.md")
        assertTrue(texto.contains("DPO"), "HANDOFF sem a pendência do DPO")
        assertTrue(texto.contains("Tarefas 4 a 9"), "HANDOFF sem o estado das Tarefas 4 a 9 do Plano 4")
    }
}
