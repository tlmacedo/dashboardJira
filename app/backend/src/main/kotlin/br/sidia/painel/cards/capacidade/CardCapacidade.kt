package br.sidia.painel.cards.capacidade

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
import br.sidia.painel.security.Role
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao

/** Quem vê Capacidade e ausências (e os demais cards de planejamento da liderança): a liderança, não o USUARIO. */
internal val SO_LIDERANCA = setOf(Role.SQUAD_LEADER, Role.COORDENADOR, Role.GERENTE)

/**
 * Card Capacidade e ausências: mapa de 8 semanas, alertas e atrasos previstos pela capacidade (com e sem a ausência) das
 * pessoas da visão. Só liderança (USUARIO recebe 403). Filtros que valem: `squad` e `pessoa`.
 */
object CardCapacidade : CardDeLeitura<CapacidadeDto> {
    override val id = "capacidade"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CapacidadeDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val pessoas = motor.pessoas().filter { it in e.escopo.visiveis }
        val analise = CapacidadeCardCalculator(motor).calcular(pessoas, abertas, e.hoje, "planejamento v${motor.regras.versao}")
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val termino = analise.terminos[s.key]?.let { "previsto para $it" } ?: "não cabe no horizonte"
                linhaDe(s, e.escopo, "Vence em ${s.duedate}; $termino")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CapacidadeDto(
        versaoRegras = "", alertas = emptyList(), atrasosPrevistos = 0, atrasosPorAusencia = 0, jain = null, mapa = emptyList(),
        ausencias = emptyList(), feriados = emptyList(), listaAtrasos = emptyList(), avisos = avisos,
    )
}

/** Cartões da tela Capacidade e ausências (inventário `CA-01` a `CA-09`). Só os atrasos têm tasks por trás. */
internal fun cartoesDeCapacidade(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    fun cartao(n: Int, natureza: Natureza, temDrill: Boolean, aceita: (String) -> Boolean, numeros: (CapacidadeDto) -> Map<String, Int>): CartaoRegistrado =
        CartaoDeLeitura(
            metaDeTrabalho(
                "CA-0$n", CardCapacidade.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, natureza, fontes, temDrill = temDrill, papeis = SO_LIDERANCA,
            ),
            CardCapacidade, aceita, numeros,
        )
    val semTasks = { _: String -> false }
    val nenhum = { _: CapacidadeDto -> emptyMap<String, Int>() }
    return listOf(
        cartao(1, Natureza.PESSOAS, false, semTasks, nenhum),
        cartao(2, Natureza.PRAZO, true, { it == "atrasosPrevistos" }) { mapOf("atrasosPrevistos" to it.atrasosPrevistos) },
        cartao(3, Natureza.PRAZO, true, { it == "atrasosPorAusencia" }) { mapOf("atrasosPorAusencia" to it.atrasosPorAusencia) },
        cartao(4, Natureza.PESSOAS, false, semTasks, nenhum),
        cartao(5, Natureza.PESSOAS, false, semTasks, nenhum),
        cartao(6, Natureza.PESSOAS, false, semTasks, nenhum),
        cartao(7, Natureza.PESSOAS, false, semTasks, nenhum),
        cartao(8, Natureza.PESSOAS, false, semTasks, nenhum),
        cartao(9, Natureza.PRAZO, true, { it == "atrasosPrevistos" }) { mapOf("atrasosPrevistos" to it.listaAtrasos.size) },
    )
}
