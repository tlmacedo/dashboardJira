package br.sidia.painel.cards.riscos

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import java.time.Duration

/** Quantas linhas a tabela de riscos leva na resposta (a contagem e a gaveta trazem todas). */
const val LIMITE_DE_LINHAS_DE_RISCO = 200

/**
 * Card Riscos: score explicável das tasks abertas dentro do escopo de quem consulta (pesos em `risco.yaml`). O histórico
 * de 16 semanas (P50/P85 por tipo e mediana por status) segue o escopo, não os filtros de tipo e prioridade.
 */
object CardRiscos : CardDeLeitura<RiscosDto> {
    override val id = "riscos"
    override val periodoPadraoDias: Int? = null

    private const val SEMANAS_DE_HISTORICO = 16L

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<RiscosDto> {
        val ctx = e.ctx
        val map = ctx.statusMap
        val regras = ctx.regrasDeRisco
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) && map.category(it.status) != StatusCategory.DONE }
        val historico = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS,
                resolvidasDe = e.agora.minus(Duration.ofDays(SEMANAS_DE_HISTORICO * 7)), osVersion = e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(map, abertas, historico)

        val temCampos = e.store.temCamposDeTrabalho(INSTANCIA_TRABALHO)
        // Previsão depois do vencimento: o término pela capacidade (Fase 6). A fila de cada pessoa usa todas as abertas dela
        // dentro da visão, sem os filtros de tipo, prioridade e OS.
        val filaDaVisao = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        val previsao = MotorDePessoas(ctx).agenda.comoPrevisao(filaDaVisao, e.hoje)
        val calc = RiscoCalculator(ctx.calendar, map, regras, previsao)
        val riscos = calc.avaliar(abertas, historico, e.agora, temCampos)
        val porChave = abertas.associateBy { it.key }

        val avisos = mutableListOf<AvisoDto>()
        avisos += Fluxo.avisosDeAmostra(calc.faixasPorTipo(historico))
        if (!temCampos) {
            avisos += AvisoDto(AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS, AvisoDto.INFORMATIVO, "prioridade e estimativas ainda não sincronizadas")
        }

        fun nivel(n: String) = riscos.filter { it.nivel == n }
        val emRisco = riscos.filter { it.nivel != "baixo" }
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        fun poeSegmento(nome: String, lista: List<Risco>) {
            segmentos[nome] = lista.mapNotNull { porChave[it.key] }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        }
        poeSegmento("nivel:alto", nivel("alto"))
        poeSegmento("nivel:medio", nivel("medio"))
        poeSegmento("nivel:baixo", nivel("baixo"))
        poeSegmento("emRisco", emRisco)

        // Sinais por pessoa: proporção (em risco sobre abertas), só da visão, em ordem alfabética.
        val emRiscoPorChave = emRisco.map { it.key }.toSet()
        val porPessoa = abertas.filter { it.assignee != null && it.assignee in e.escopo.visiveis }.groupBy { requireNotNull(it.assignee) }
        for (p in porPessoa.keys.sorted()) {
            val todas = porPessoa.getValue(p)
            segmentos["pessoa:$p:abertas"] = todas.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
            segmentos["pessoa:$p:emRisco"] = todas.filter { it.key in emRiscoPorChave }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        }

        val dto = RiscosDto(
            versaoRegras = "risco v${regras.versao}",
            alto = nivel("alto").size, medio = nivel("medio").size, baixo = nivel("baixo").size, emRisco = emRisco.size,
            limiteAlto = regras.alto, limiteMedio = regras.medio,
            linhas = emRisco.take(LIMITE_DE_LINHAS_DE_RISCO).mapNotNull { r ->
                val s = porChave[r.key] ?: return@mapNotNull null
                LinhaRiscoDto(
                    key = s.key, resumo = s.summary, tipo = s.issueType, status = s.status, responsavel = s.assignee,
                    pontos = r.pontos, nivel = r.nivel, fatorPrincipal = r.fatores.first().first.name,
                    fatores = r.fatores.map { (f, p) -> FatorPontosDto(f.name, p) },
                )
            },
            sinaisPorPessoa = porPessoa.keys.sorted().map { p ->
                SinaisPessoaDto(p, segmentos.getValue("pessoa:$p:emRisco").size, porPessoa.getValue(p).size)
            },
            avisos = avisos,
        )
        val riscoPorChave = riscos.associateBy { it.key }
        return ResultadoDeCard(dto) { segmento ->
            segmentos[segmento]?.map { s -> linhaDe(s, e.escopo, detalhe(riscoPorChave[s.key])) }
        }
    }

    /** "85 pontos (alto): idade acima do P85 do tipo 30; parada no status 15; ..." */
    private fun detalhe(r: Risco?): String {
        if (r == null) return ""
        val fatores = if (r.fatores.isEmpty()) "sem fator de risco" else r.fatores.joinToString("; ") { (f, p) -> "${f.rotulo} $p" }
        return "${r.pontos} pontos (${r.nivel}): $fatores"
    }

    override fun vazio(avisos: List<AvisoDto>) = RiscosDto(
        versaoRegras = "", alto = 0, medio = 0, baixo = 0, emRisco = 0, limiteAlto = 0, limiteMedio = 0, linhas = emptyList(),
        sinaisPorPessoa = emptyList(), avisos = avisos,
    )
}

/** Cartões da tela Riscos (inventário `RI-01` a `RI-03`). */
internal fun cartoesDeRiscos(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun cartao(n: Int, natureza: Natureza, aceita: (String) -> Boolean, numeros: (RiscosDto) -> Map<String, Int>): CartaoRegistrado =
        CartaoDeLeitura(
            metaDeTrabalho("RI-0$n", CardRiscos.id, GrupoDeCartao.TRABALHO_EM_CURSO, natureza, fontes, exigeChangelog = true),
            CardRiscos, aceita, numeros,
        )
    return listOf(
        cartao(1, Natureza.ESTOQUE, { it == "nivel:alto" || it == "nivel:medio" }) { mapOf("nivel:alto" to it.alto, "nivel:medio" to it.medio) },
        cartao(2, Natureza.ESTOQUE, { it == "emRisco" || it == "nivel:baixo" }) { mapOf("emRisco" to it.emRisco, "nivel:baixo" to it.baixo) },
        cartao(3, Natureza.PESSOAS, { it.startsWith("pessoa:") }) { dto ->
            buildMap {
                for (p in dto.sinaisPorPessoa) {
                    put("pessoa:${p.pessoa}:emRisco", p.issuesEmRisco)
                    put("pessoa:${p.pessoa}:abertas", p.abertas)
                }
            }
        },
    )
}
