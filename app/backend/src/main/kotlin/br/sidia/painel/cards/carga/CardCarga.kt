package br.sidia.painel.cards.carga

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
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.SqliteWorklogStore
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import java.time.Duration
import java.util.Locale

/**
 * Card Carga por pessoa: fila, capacidade e ocupação de 4 semanas das pessoas da visão (equipe cadastrada). Só os filtros
 * `squad` e `pessoa` valem: a fila inteira da pessoa é o que ocupa a capacidade dela, então tipo, prioridade e OS não a
 * recortam. Todos os papéis veem o card; o USUARIO recebe só a própria linha e nenhum agregado do squad.
 */
object CardCarga : CardDeLeitura<CargaDto> {
    override val id = "carga"
    override val periodoPadraoDias: Int? = null

    private const val SEMANAS_DE_HISTORICO = 16L
    private const val DIAS_DE_HORAS_REGISTRADAS = 30L

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CargaDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        val historico = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = e.agora.minus(Duration.ofDays(SEMANAS_DE_HISTORICO * 7)),
            ),
        ).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, historico)

        val pessoas = motor.pessoas().filter { it in e.escopo.visiveis }
        // Horas registradas nos últimos 30 dias (worklog sincronizado, sem comentário); só a linha das pessoas da visão as mostra.
        val horasRegistradas = ctx.dataSource?.let {
            SqliteWorklogStore(it).horasPorAutor(INSTANCIA_TRABALHO, e.agora.minus(Duration.ofDays(DIAS_DE_HORAS_REGISTRADAS)), e.agora)
        }
        val analise = CargaCalculator(motor, ctx.regrasDeRisco).calcular(
            pessoas, abertas, historico, e.hoje, e.agora, e.store.temCamposDeTrabalho(INSTANCIA_TRABALHO), "planejamento v${motor.regras.versao}",
            horasRegistradas,
        )
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val h = motor.horas.de(s)
                val restam = String.format(Locale.ROOT, "Restam %.1f h%s", h.valor, if (h.assumidas) " (horas assumidas)" else "")
                linhaDe(s, e.escopo, s.duedate?.let { "$restam; vence em $it" } ?: restam)
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CargaDto(
        versaoRegras = "", jain = null, acimaDaCapacidade = 0, comFolga = 0, tasksAcimaDaCapacidade = 0, tasksComFolga = 0,
        linhas = emptyList(), avisos = avisos,
    )
}

/**
 * Cartões da tela Carga por pessoa (inventário `CP-01` a `CP-03`). O Jain não tem tasks por trás (é um índice do conjunto)
 * e o CP-02 abre as tasks na fila das pessoas contadas (o texto diz isso).
 */
internal fun cartoesDeCarga(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun cartao(n: Int, temDrill: Boolean, aceita: (String) -> Boolean, numeros: (CargaDto) -> Map<String, Int>): CartaoRegistrado =
        CartaoDeLeitura(
            metaDeTrabalho(
                "CP-0$n", CardCarga.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PESSOAS, fontes, exigeChangelog = true, temDrill = temDrill,
            ),
            CardCarga, aceita, numeros,
        )
    return listOf(
        cartao(1, false, { false }) { emptyMap() },
        cartao(2, true, { it == "acimaDaCapacidade" || it == "comFolga" }) {
            mapOf("acimaDaCapacidade" to it.tasksAcimaDaCapacidade, "comFolga" to it.tasksComFolga)
        },
        cartao(3, true, { it.startsWith("pessoa:") }) { dto ->
            buildMap {
                for (l in dto.linhas) {
                    put("pessoa:${l.pessoa}:fila", l.abertas)
                    put("pessoa:${l.pessoa}:wip", l.wip)
                    put("pessoa:${l.pessoa}:vencidas", l.vencidas)
                    put("pessoa:${l.pessoa}:acimaDoP85", l.acimaDoP85)
                    put("pessoa:${l.pessoa}:riscoAlto", l.riscoAlto)
                }
            }
        },
    )
}
