package br.sidia.painel.inicio

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import br.sidia.painel.cards.fechadas.SemanaDto
import br.sidia.painel.cards.previsao.QuandoDto
import kotlinx.serialization.Serializable

/**
 * Um indicador da Central de atenção. [item] é o nome estável do indicador (a tela traduz por `central.item.<NOME>`);
 * [n] é o número (nulo = indisponível, por exemplo com status fora do mapa) e [universo] o total de que ele faz parte, quando
 * há um (metas e estimativa). [valor] é o mesmo número em texto, sem formatação por idioma ("7" ou "7/20"). [estado] é `ok`,
 * `atencao`, `problema`, `neutro` ou `indisponivel`. [rota] é a tela de destino e [cartao]/[segmento] abrem as tasks pelo drill
 * genérico dos cartões: o [n] é o total desse drill (o mesmo número do card de destino). Sem [cartao], o indicador não tem tasks.
 */
@Serializable
data class ItemCentralDto(
    val item: String,
    val valor: String,
    val n: Int?,
    val universo: Int?,
    val estado: String,
    val rota: String,
    val cartao: String?,
    val segmento: String?,
)

/**
 * VG-12: o cartão de um squad. [ocupacaoMedia] é a média das ocupações de 4 semanas das pessoas do squad com capacidade (o
 * estado vem das faixas de `planejamento.yaml`: verde até 85%, laranja até 110%, vermelho acima) e [jain] o índice do squad.
 * É do CONJUNTO: nunca traz nota de pessoa. A lista vem em ordem alfabética de squad.
 */
@Serializable
data class CartaoSquadDto(val squad: String, val ocupacaoMedia: OcupacaoDto, val jain: Double?, val pessoas: Int)

/**
 * A Central de atenção (COORDENADOR e GERENTE). [kpis] e [itens] são indicadores (cada um é link para a tela de destino e
 * abre as tasks); SEM_ATUALIZACAO e PARADAS_NO_STATUS são itens SEPARADOS. O item INDICIO_ALTO só existe para o GERENTE e só
 * com a trava do DPO aberta; para os demais ele não está no DTO. [entregasPorSemana] são as últimas 12 semanas (VG-10),
 * [backlogTermina] o Monte Carlo do backlog (VG-11) e [squads] o VG-12. Se um status está fora do mapa, [avisos] traz o aviso
 * bloqueante e os números dependentes dele ficam indisponíveis (nunca zero).
 */
@Serializable
data class CentralDto(
    val kpis: List<ItemCentralDto>,
    val itens: List<ItemCentralDto>,
    val entregasPorSemana: List<SemanaDto>,
    val backlogTermina: QuandoDto?,
    val backlog: Int?,
    val confiancaDoBacklog: String?,
    val squads: List<CartaoSquadDto>,
    val avisos: List<AvisoDto>,
)
