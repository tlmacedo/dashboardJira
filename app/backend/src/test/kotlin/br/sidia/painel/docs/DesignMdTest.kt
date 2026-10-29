package br.sidia.painel.docs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Documentos de identidade visual (Plano 15): registro dos ativos de marca (T1) e `DESIGN.md` (T5). */
class DesignMdTest {
    private fun doc(relativo: String): String {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val f = dir.resolve(relativo)
            if (Files.exists(f)) return Files.readString(f)
            dir = dir.parent
        }
        error("$relativo não encontrado")
    }

    @Test
    fun ativosDeMarcaRegistrados() {
        val texto = doc("docs/design/ativos-de-marca.md").lowercase()
        // As três decisões do ponto de parada da Tarefa 1: logo, fonte e licença, manual de marca.
        for (tema in listOf("logo", "fonte", "licença", "manual de marca", "ruling:", "como reverter", "#1329a0")) {
            assertTrue(tema in texto, "ativos-de-marca.md não cita: $tema")
        }
    }

    /** Os três temas, na ordem em que a tabela do `DESIGN.md` os lista. */
    private val temas = listOf("claro", "escuro", "sidia")

    /**
     * Lê `tokens.css` e devolve, por token, o valor EFETIVO em cada tema: o do bloco do tema, ou o de `:root` quando o tema
     * não redefine o token (é assim que a cascata do CSS resolve `[data-theme=...]` sobre a raiz).
     */
    private fun tokensDoCss(): Map<String, Map<String, String>> {
        val css = doc("app/frontend/src/theme/tokens.css").replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val blocos = Regex("""(:root|\[data-theme='(\w+)'\])\s*\{([^}]*)\}""").findAll(css).associate { m ->
            val tema = when (m.groupValues[2]) {
                "" -> "claro"
                "dark" -> "escuro"
                else -> m.groupValues[2]
            }
            tema to m.groupValues[3].split(";").mapNotNull { decl ->
                val i = decl.indexOf(':')
                val nome = decl.substring(0, maxOf(i, 0)).trim()
                if (i < 0 || !nome.startsWith("--")) null else nome to decl.substring(i + 1).trim()
            }.toMap()
        }
        assertEquals(setOf("claro", "escuro", "sidia"), blocos.keys, "tokens.css deve ter os três temas")
        val todos = blocos.values.flatMap { it.keys }.toSortedSet()
        return todos.associateWith { token ->
            temas.associateWith { tema -> blocos[tema]!![token] ?: blocos["claro"]!![token] ?: "(não definido)" }
        }
    }

    /** Linhas `| `--token` | `claro` | `escuro` | `sidia` | uso |` da tabela de tokens do `DESIGN.md`. */
    private fun tokensDoDesignMd(): Map<String, Map<String, String>> {
        val linha = Regex("""^\|\s*`(--[\w-]+)`\s*\|\s*`([^`]*)`\s*\|\s*`([^`]*)`\s*\|\s*`([^`]*)`\s*\|""", RegexOption.MULTILINE)
        return linha.findAll(doc("DESIGN.md")).associate { m ->
            m.groupValues[1] to temas.mapIndexed { i, tema -> tema to m.groupValues[2 + i].trim() }.toMap()
        }
    }

    @Test
    fun designMdBateComTokens() {
        val css = tokensDoCss()
        val md = tokensDoDesignMd()
        val erros = mutableListOf<String>()
        for ((token, porTema) in css) {
            val linha = md[token]
            if (linha == null) {
                erros += "token $token está no tokens.css e falta no DESIGN.md"
                continue
            }
            for (tema in temas) {
                val noCss = porTema.getValue(tema)
                val noMd = linha.getValue(tema)
                if (!noCss.equals(noMd, ignoreCase = true)) erros += "token $token, tema $tema: tokens.css=\"$noCss\" DESIGN.md=\"$noMd\""
            }
        }
        for (token in md.keys - css.keys) erros += "token $token está no DESIGN.md e não existe no tokens.css"
        assertTrue(css.size >= 30, "tokens.css com poucos tokens: ${css.size}")
        assertTrue(erros.isEmpty(), "DESIGN.md diverge do tokens.css:\n" + erros.joinToString("\n"))
    }

    @Test
    fun designMdCitaOsTresTemas() {
        val texto = doc("DESIGN.md")
        for (tema in listOf("Claro", "Escuro", "Sidia")) assertTrue("**$tema**" in texto || "| $tema" in texto, "DESIGN.md não descreve o tema $tema")
        // O tema é escolhido por data-theme na raiz, com os valores que o ThemeProvider grava.
        for (valor in listOf("data-theme", "light", "dark", "sidia")) assertTrue(valor in texto, "DESIGN.md não cita $valor")
    }

    @Test
    fun designMdCitaCadaComponenteDoKit() {
        val texto = doc("DESIGN.md")
        val obrigatorios = listOf(
            "shared/Cartao.tsx", "shared/GavetaDeTasks.tsx", "shared/TabelaVirtual.tsx", "shared/EstadoDaTela.tsx",
            "shared/CardPage.tsx", "shared/Filtros.tsx", "shared/AjudaDoCartao.tsx", "shell/Marca.tsx",
            "shared/graficos/Barras.tsx", "shared/graficos/Linhas.tsx", "shared/graficos/Dispersao.tsx",
            "shared/graficos/TabelaEquivalente.tsx", "shared/graficos/paleta.ts",
        )
        for (arquivo in obrigatorios) assertTrue("`$arquivo`" in texto, "DESIGN.md não cita `$arquivo`")
        // Todo arquivo de componente citado existe de verdade (o documento não descreve o que não há).
        val citados = Regex("""`((?:shared|shell|theme)/[\w/.-]+\.(?:tsx|ts|css))`""").findAll(texto).map { it.groupValues[1] }.toSet()
        val inexistentes = citados.filterNot { ApoioDeDocs.existe("app/frontend/src/$it") }
        assertTrue(inexistentes.isEmpty(), "DESIGN.md cita arquivos que não existem: $inexistentes")
    }

    @Test
    fun designMdCitaRegrasDeAcessibilidade() {
        val texto = doc("DESIGN.md")
        for (regra in listOf("4,5:1", "3:1", "2 px", "prefers-reduced-motion", "WCAG AA")) assertTrue(regra in texto, "DESIGN.md não cita a regra: $regra")
    }

    @Test
    fun designMdCitaSimbolosEPaletaDosGraficos() {
        val texto = doc("DESIGN.md")
        for (simbolo in listOf("✓", "●", "▲", "■")) assertTrue(simbolo in texto, "DESIGN.md não cita o símbolo $simbolo")
        // Padrões e formas da paleta categórica vêm de paleta.ts: o documento lista os mesmos.
        val paleta = ApoioDeDocs.texto("app/frontend/src/shared/graficos/paleta.ts")
        for (lista in listOf("PADROES", "FORMAS")) {
            val itens = Regex("""export const $lista = \[([^\]]*)\]""").find(paleta)!!.groupValues[1].split(",").map { it.trim().trim('\'') }.filter { it.isNotEmpty() }
            assertTrue(itens.size == 6, "paleta.ts mudou o tamanho de $lista")
            for (item in itens) assertTrue("`$item`" in texto, "DESIGN.md não cita `$item` ($lista)")
        }
    }

    @Test
    fun designMdDizOQueNaoFazer() {
        val texto = doc("DESIGN.md").lowercase()
        for (regra in listOf("ranking", "cor como único sinal", "laranja", "verde")) assertTrue(regra in texto, "DESIGN.md não cita o que não fazer: $regra")
    }
}
