package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Material de treinamento por papel (Plano 15, Tarefa 8). Cada material só fala das telas e dos cartões que o papel vê
 * (menu em `nav.ts` e catálogo `catalogoPadrao()`), a Integridade e o Registro de acesso só aparecem no material do gerente,
 * as capturas são só de demonstração e nada cita pessoa ou endereço de rede.
 */
class TreinamentoDocTest {
    private val materiais = mapOf(
        "docs/treinamento/usuario.md" to "USUARIO",
        "docs/treinamento/squad-leader.md" to "SQUAD_LEADER",
        "docs/treinamento/coordenador.md" to "COORDENADOR",
        "docs/treinamento/gerente.md" to "GERENTE",
    )
    private val roteiro = "docs/treinamento/roteiro-da-sessao.md"
    private val manifesto = "docs/treinamento/img/manifesto.yaml"

    private val inicioDoPapel = mapOf("USUARIO" to "meu", "SQUAD_LEADER" to "squad", "COORDENADOR" to "central", "GERENTE" to "central")

    private fun idsVisiveis(papel: String): Set<String> = ApoioDeDocs.menu().filter { papel in it.papeis }.map { it.id }.toSet()

    @Test
    fun umMaterialPorPapel() {
        for (arquivo in materiais.keys + roteiro + manifesto) assertTrue(ApoioDeDocs.existe(arquivo), "falta $arquivo")
    }

    @Test
    fun cadaMaterialTemAsPartesDoPlano() {
        for (arquivo in materiais.keys) {
            val texto = ApoioDeDocs.texto(arquivo)
            for (parte in listOf("## Objetivo da sessão", "## Antes de começar", "## Roteiro por tela", "## Exercício com dados de demonstração", "## Para saber mais")) {
                assertTrue(parte in texto, "$arquivo sem a parte \"$parte\"")
            }
            assertTrue("30 a 45 min" in texto, "$arquivo sem a duração (30 a 45 min)")
            assertTrue("perguntas-frequentes.md" in texto, "$arquivo sem link para as perguntas frequentes")
            assertTrue("dados de demonstração" in texto, "$arquivo não diz que o exercício usa dados de demonstração")
            // Cada tela do roteiro traz a pergunta, onde clicar, como ler o número e a armadilha comum.
            val telas = texto.split(Regex("(?m)^(?=### Tela `)")).drop(1)
            assertTrue(telas.isNotEmpty(), "$arquivo sem telas no roteiro")
            for (tela in telas) {
                val id = Regex("^### Tela `(\\w+)`").find(tela)!!.groupValues[1]
                for (rotulo in listOf("**Pergunta.**", "**Onde clicar.**", "**Como ler o número.**", "**Armadilha comum.**")) assertTrue(rotulo in tela, "$arquivo, tela $id sem $rotulo")
            }
        }
    }

    @Test
    fun treinamentoSoComTelasDoPapel() {
        val todos = ApoioDeDocs.menu().map { it.id }.toSet()
        for ((arquivo, papel) in materiais) {
            val texto = ApoioDeDocs.texto(arquivo)
            val visiveis = idsVisiveis(papel)
            val roteiroDeTelas = Regex("(?m)^### Tela `(\\w+)`").findAll(texto).map { it.groupValues[1] }.toList()
            assertTrue(roteiroDeTelas.size >= 4, "$arquivo com poucas telas no roteiro: $roteiroDeTelas")
            assertEquals(roteiroDeTelas.size, roteiroDeTelas.toSet().size, "$arquivo repete tela")
            assertTrue(inicioDoPapel.getValue(papel) in roteiroDeTelas, "$arquivo não começa pela tela inicial do papel (${inicioDoPapel.getValue(papel)})")
            val proibidas = roteiroDeTelas.filterNot { it in visiveis }
            assertTrue(proibidas.isEmpty(), "$arquivo cita telas que o papel $papel não vê: $proibidas")
            // Qualquer id de tela citado entre crases, em qualquer parte do texto, também tem de ser visível para o papel.
            val citadas = Regex("`(\\w+)`").findAll(texto).map { it.groupValues[1] }.filter { it in todos }.toSet()
            val fora = citadas - visiveis
            assertTrue(fora.isEmpty(), "$arquivo cita, entre crases, telas que $papel não vê: $fora")
        }
    }

    @Test
    fun treinamentoSoComCartoesDoPapel() {
        val catalogo = ApoioDeDocs.cartoes()
        for ((arquivo, papel) in materiais) {
            val citados = ApoioDeDocs.idsDeCartaoCitados(ApoioDeDocs.texto(arquivo))
            val inexistentes = citados - catalogo.keys
            assertTrue(inexistentes.isEmpty(), "$arquivo cita cartões que não existem no catálogo: $inexistentes")
            val proibidos = citados.filter { papel !in catalogo.getValue(it).papeis.map { p -> p.name } }
            assertTrue(proibidos.isEmpty(), "$arquivo cita cartões que o papel $papel não vê: $proibidos")
        }
        // O roteiro da sessão é comum a todos: não cita cartão de nenhum papel em particular.
        assertTrue(ApoioDeDocs.idsDeCartaoCitados(ApoioDeDocs.texto(roteiro)).isEmpty(), "o roteiro comum não cita cartões")
    }

    @Test
    fun integridadeSoNoMaterialDoGerente() {
        val sensivel = Regex("integridade|registro de acesso|registroAcesso|VG-13|\\bIN-\\d{2}\\b|\\bRA-\\d{2}\\b", RegexOption.IGNORE_CASE)
        for (arquivo in materiais.keys + roteiro) {
            val texto = ApoioDeDocs.texto(arquivo)
            if (arquivo.endsWith("gerente.md")) {
                for (trecho in listOf("Integridade", "Registro de acesso", "Indício, não prova", "AGUARDANDO_ACEITE_DPO", "auditad")) assertTrue(trecho in texto, "gerente.md sem: $trecho")
            } else {
                assertTrue(!sensivel.containsMatchIn(texto), "$arquivo fala de Integridade ou do Registro de acesso: ${sensivel.find(texto)?.value}")
            }
        }
    }

    @Test
    fun todoMaterialTemMeusDadosESemPendenciaDoPlano11() {
        for (arquivo in materiais.keys) {
            val texto = ApoioDeDocs.texto(arquivo)
            assertTrue("### Tela `meusDados`" in texto, "$arquivo sem a tela Meus dados")
            assertTrue("plano 11" !in texto.lowercase(), "$arquivo ainda trata o Plano 11 como futuro")
            for (trecho in listOf("24 meses", "200", "\"dev\"", "decisões de distribuição")) assertTrue(trecho in texto, "$arquivo sem \"$trecho\" na tela Meus dados")
        }
        // A Auditoria está atrás da Trava do DPO: os materiais da coordenação e do gerente dizem isso e citam o código.
        for (arquivo in listOf("docs/treinamento/coordenador.md", "docs/treinamento/gerente.md")) {
            val texto = ApoioDeDocs.texto(arquivo)
            assertTrue("Trava do DPO" in texto && "AGUARDANDO_ACEITE_DPO" in texto && "VG-14" in texto, "$arquivo não descreve a Auditoria atrás da trava")
        }
    }

    @Test
    fun materialDizOEstadoDeHoje() {
        for (arquivo in materiais.keys + roteiro) {
            val texto = ApoioDeDocs.texto(arquivo)
            assertTrue("JIRA_LIVE_ENABLED=false" in texto, "$arquivo não diz o estado da flag do Jira")
            assertTrue("Papel (simulação)" in texto || arquivo == roteiro, "$arquivo não explica como escolher o papel na demonstração")
        }
        val roteiroTexto = ApoioDeDocs.texto(roteiro)
        for (trecho in listOf("papeis.yaml", "Pergunta", "## Agenda", "## Pré-requisitos", "## Perguntas para fechar a sessão")) assertTrue(trecho in roteiroTexto, "roteiro sem: $trecho")
    }

    @Test
    fun linksDoTreinamentoExistem() {
        for (arquivo in materiais.keys + roteiro) {
            val base = TestPaths.repoDir.resolve(arquivo).parent
            val quebrados = Regex("\\]\\(([^)#]+)(?:#[^)]*)?\\)").findAll(ApoioDeDocs.texto(arquivo)).map { it.groupValues[1] }
                .filterNot { it.startsWith("http") }.filterNot { Files.exists(base.resolve(it)) }.toList()
            assertTrue(quebrados.isEmpty(), "$arquivo tem links quebrados: $quebrados")
        }
    }

    @Test
    fun capturasSoDeDemonstracao() {
        val texto = ApoioDeDocs.texto(manifesto)
        val entradas = texto.split(Regex("(?m)^\\s*- arquivo:")).drop(1)
        val listados = entradas.map { it.lineSequence().first().trim() }
        for (e in entradas) {
            val nome = e.lineSequence().first().trim()
            for (campo in listOf("tela:", "tema:", "idioma:", "data:", "demo: true")) assertTrue(campo in e, "manifesto: $nome sem $campo")
        }
        val pasta = TestPaths.repoDir.resolve("docs/treinamento/img")
        val imagens = Files.list(pasta).use { f -> f.filter { it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "svg") }.map { it.name }.toList() }
        val foraDoManifesto = imagens.filterNot { it in listados }
        assertTrue(foraDoManifesto.isEmpty(), "imagens fora do manifesto (ou sem demo: true): $foraDoManifesto")
        val semArquivo = listados.filterNot { it in imagens }
        assertTrue(semArquivo.isEmpty(), "manifesto lista imagens que não existem: $semArquivo")
    }

    @Test
    fun materialSemNomesNemHosts() {
        for (arquivo in materiais.keys + roteiro + manifesto) {
            val texto = ApoioDeDocs.texto(arquivo)
            assertTrue(ApoioDeDocs.enderecosDeRede(texto).isEmpty(), "$arquivo cita endereço de rede: ${ApoioDeDocs.enderecosDeRede(texto)}")
            assertTrue(!Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+").containsMatchIn(texto), "$arquivo cita e-mail")
            // Nomes de usuário do Jira têm o formato nome.sobrenome (ex.: dev.alfa na demonstração): nenhum aparece.
            assertTrue(!Regex("`[a-z]+\\.[a-z]+`").containsMatchIn(texto.replace(Regex("`[\\w-]+\\.(?:yaml|md|json|ts|tsx|css|sh|ps1)`"), "")), "$arquivo cita nome de usuário")
        }
    }
}
