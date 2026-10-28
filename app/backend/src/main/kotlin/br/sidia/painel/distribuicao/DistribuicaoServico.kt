package br.sidia.painel.distribuicao

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.DrillRow
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.cards.competencias.competenciasPedidas
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Visibility
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.WorkStore
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Id da ficha (`distribuicao.card.yaml`) que manda os tipos e os status excluídos da leitura. */
internal const val FICHA_DISTRIBUICAO = "distribuicao"

/** Horizonte da carga e da capacidade, em semanas (o mesmo da Carga por pessoa). */
private const val SEMANAS = 4

/** Janela do histórico (produtividade, afinidade e última atribuição), em semanas. */
private const val SEMANAS_DE_HISTORICO = 16L

/**
 * O que a Distribuição e o Rebalanceamento veem para quem pergunta, num instante. Tudo já recortado pela VISÃO do papel: o
 * líder de squad só tem candidatos, origens e destinos do próprio squad. [bloqueio] é o aviso bloqueante (status sem mapeamento),
 * caso em que as listas vêm vazias.
 */
class AnaliseDeDistribuicao(
    val versaoRegras: String,
    val resultado: ResultadoDistribuicao,
    val pendentes: List<IssueSnapshot>,
    val horas: Map<String, Double>,
    val horasAssumidas: Set<String>,
    val movimentos: List<Movimento>,
    val tasks: Map<String, IssueSnapshot>,
    val candidatos: List<String>,
    /** Pessoas da visão que existem na equipe cadastrada (podem ser "escolhido" numa troca), mesmo sem capacidade. */
    val pessoasDaVisao: Set<String>,
    val visiveis: Set<String>,
    val avisos: List<AvisoDto>,
) {
    val sugestoes: Map<String, Sugestao> = resultado.sugestoes.associateBy { it.issueKey }
    val movimentoPorTask: Map<String, Movimento> = movimentos.associateBy { it.issueKey }

    /** Linha da gaveta; o responsável só aparece se estiver na visão de quem pediu. */
    fun linha(s: IssueSnapshot, detalhe: String): DrillRow = DrillRow(s.key, s.summary, s.assignee?.takeIf { it in visiveis }, s.status, detalhe)
}

/**
 * Monta a análise da Distribuição (modo sombra) e do Rebalanceamento a partir do que o painel já sabe (SQLite): nada aqui toca
 * o Jira nem grava. Horas, capacidade, demanda e WIP vêm do motor de pessoas (Plano 6); as regras, de `distribuicao.yaml`.
 */
class DistribuicaoServico(private val ctx: AppContext, private val store: WorkStore) {
    fun analisar(principal: Principal, hoje: LocalDate, agora: Instant): AnaliseDeDistribuicao {
        val regras = ctx.regrasDistribuicao
        val map = ctx.statusMap
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(FICHA_DISTRIBUICAO))
        val abertas = store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS))
        val historico = store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = agora.minus(Duration.ofDays(SEMANAS_DE_HISTORICO * 7)),
            ),
        )
        Fluxo.exigirStatusMapeados(map, abertas, historico)

        // A visão: quem o papel enxerga entre quem existe na base e na equipe cadastrada. O líder de squad só vê o próprio squad.
        val visiveis = Visibility.visibleUserIds(principal, ctx.roles, hoje, store.pessoas(INSTANCIA_TRABALHO) + ctx.equipes.todos.map { it.id })
        val naoConcluidas = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val pessoas = motor.pessoas().filter { it in visiveis }

        val distribuidor = Distribuidor(regras, motor.regras.tetoJain)
        // Pendentes: as que ninguém executa. Sem responsável não é dado de ninguém; com o gerente só aparece para quem vê o gerente.
        val pendentes = distribuidor.pendentes(abertas, map, ctx.roles, hoje).filter { it.assignee == null || it.assignee in visiveis }

        val candidatos = pessoas.map { p ->
            val carga = motor.demanda.carga(p, naoConcluidas, hoje, SEMANAS)
            val doHistorico = historico.filter { it.assignee == p }
            val dele = naoConcluidas.filter { it.assignee == p }
            Candidato(
                pessoa = p,
                cargaProjetadaH = carga.semanas.sumOf { it.demandaH },
                capacidadeH = carga.semanas.sumOf { it.capacidadeH },
                wip = dele.count { Fluxo.emAndamento(it, map) },
                limiteWip = motor.regras.wipPorPessoa,
                competenciasFaltantes = 0,
                jaTrabalhouNoEpicoOuComponente = false,
                ultimaAtribuicao = (abertas + historico).mapNotNull { s -> s.timeline.assigneeChanges.filter { it.to == p }.maxOfOrNull { it.at } }.maxOrNull(),
                produtividade = produtividadeDe(doHistorico, map),
                competencias = motor.equipes.de(p)?.competencias.orEmpty(),
                afinidades = (dele + doHistorico).flatMap { afinidadeDe(it) }.toSet(),
            )
        }

        val itens = pendentes.map { it to motor.horas.de(it).valor }
        val resultado = distribuidor.distribuir(itens, candidatos, ::competenciasPedidas, ::afinidadeDe)

        // Rebalanceamento: origem e destino só entre as pessoas da visão; só tasks não iniciadas.
        val cargas = pessoas.associateWith { motor.demanda.carga(it, naoConcluidas, hoje, SEMANAS) }
        val naoIniciadas = pessoas.associateWith { p ->
            Rebalanceador.naoIniciadas(naoConcluidas.filter { it.assignee == p }, map).map { it to motor.horas.de(it).valor }
        }
        val competencias = pessoas.associateWith { motor.equipes.de(it)?.competencias.orEmpty() }
        // A ordem em que o motor aplica os movimentos segue a ocupação da origem (quem está mais carregado primeiro): quem consulta
        // recebe a lista em ordem natural de chave, para a ordem nunca virar um ranking de pessoas.
        val movimentos = Rebalanceador(regras).calcular(cargas, naoIniciadas, competencias, ::competenciasPedidas)
            .sortedWith { a, b -> ORDEM_NATURAL_DE_CHAVES.compare(a.issueKey, b.issueKey) }

        val tasks = (pendentes + naoIniciadas.values.flatten().map { it.first }).associateBy { it.key }
        val assumidas = (pendentes + naoIniciadas.values.flatten().map { it.first }).filter { motor.horas.de(it).assumidas }.map { it.key }.toSet()
        val visiveisParaAvisos = abertas.filter { it.assignee == null || it.assignee in visiveis }
        val avisos = avisosDePessoas(motor, visiveisParaAvisos, assumidas.isNotEmpty())
        val elegiveis = candidatos.filter { it.capacidadeH > regras.capacidadeMinimaH }.map { it.pessoa }.sorted()
        return AnaliseDeDistribuicao(
            versaoRegras = regras.rotulo,
            resultado = resultado,
            pendentes = pendentes,
            horas = (pendentes + naoIniciadas.values.flatten().map { it.first }).associate { it.key to motor.horas.de(it).valor },
            horasAssumidas = assumidas,
            movimentos = movimentos,
            tasks = tasks,
            candidatos = elegiveis,
            pessoasDaVisao = pessoas.toSet(),
            visiveis = visiveis,
            avisos = avisos,
        )
    }

    /** Épico e componentes de uma task: o que "já trabalhou no mesmo épico ou componente" compara. */
    private fun afinidadeDe(s: IssueSnapshot): Set<String> = setOfNotNull(s.epicKey) + s.components

    /**
     * Gasto ÷ estimado das concluídas com resolução positiva e as duas medidas (16 semanas); sem nenhuma, 1,0. O motor limita à
     * faixa das regras. Uma medida só por task: nada aqui compara colegas.
     */
    private fun produtividadeDe(concluidas: List<IssueSnapshot>, map: br.sidia.painel.domain.StatusMap): Double {
        val medidas = concluidas.filter { Fluxo.concluidaPositiva(it, map) && (it.estimateS ?: 0L) > 0L && (it.spentS ?: 0L) > 0L }
        if (medidas.isEmpty()) return 1.0
        return medidas.sumOf { it.spentS!! }.toDouble() / medidas.sumOf { it.estimateS!! }
    }
}

/** A ocupação (fração) de uma [Ocupacao.Valor]; nulo para "sem capacidade". */
internal fun Ocupacao.fracaoOuNulo(): Double? = (this as? Ocupacao.Valor)?.fracao

/** Texto do detalhe da gaveta de uma task pendente. */
internal fun detalheDePendente(s: IssueSnapshot, sugestao: Sugestao?): String {
    val onde = if (s.assignee == null) "Sem responsável" else "Com o gerente"
    return if (sugestao == null) onde else "$onde; sugestão: " + (sugestao.sugerido ?: "ninguém tem capacidade")
}
