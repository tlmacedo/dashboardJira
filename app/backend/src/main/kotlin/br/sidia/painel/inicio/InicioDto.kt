package br.sidia.painel.inicio

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import br.sidia.painel.cards.capacidade.AusenciaDto
import br.sidia.painel.cards.carga.LinhaCargaDto
import br.sidia.painel.cards.fechadas.SemanaDto
import br.sidia.painel.cards.vencimentos.VencimentosDto
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
 * A Visão do squad (`GET /api/squad`): a carga de cada pessoa do squad (ordem alfabética, sem nota), o que vence na semana, as
 * ausências e o risco. Todos os números são os dos cards Carga, Vencimentos, Capacidade e Riscos com o filtro `squad`, então a
 * gaveta de cada um é a do cartão desse card (`CP-03`, `VE-01`/`VE-02`, `RI-01`) com o mesmo `squad`.
 */
@Serializable
data class SquadDto(
    val squad: String,
    val carga: List<LinhaCargaDto>,
    val vencimentos: VencimentosDto,
    val ausencias: List<AusenciaDto>,
    val riscoAlto: Int,
    val riscoMedio: Int,
    val avisos: List<AvisoDto>,
)

/** Uma task da fila de [MeuPainelDto], na ordem da agenda: [termino] é o previsto pela capacidade e [atrasa] diz se passa do [vence]. */
@Serializable
data class ItemFilaDto(val key: String, val resumo: String, val vence: String?, val termino: String?, val atrasa: Boolean, val nivelRisco: String)

/** O esforço do mês de uma pessoa: os três componentes lado a lado e o índice composto (a proposta de `esforco.yaml`). */
@Serializable
data class EsforcoResumoDto(val pesoTipo: Double, val testes: Double, val horas: Double, val indice: Double, val versaoRegras: String)

/**
 * O Meu painel (`GET /api/meu-painel`): SEMPRE o próprio usuário (o Squad Leader pode escolher uma pessoa do squad com `pessoa=`;
 * para os outros papéis o parâmetro é ignorado). [pessoa] é de quem é o painel. Os números sobre tasks (MP-01 `minhasAbertas` e
 * `vencemEm7d`, MP-03 `emRisco`) abrem a gaveta dos cartões `MP-nn`; ficam nulos se um status está fora do mapa. [ausenciasFuturas]
 * não têm motivo.
 */
@Serializable
data class MeuPainelDto(
    val pessoa: String,
    val minhasAbertas: Int?,
    val vencemEm7d: Int?,
    val emRisco: Int?,
    val noPrazo: Int?,
    val noPrazoAjustado: Int?,
    val universoDePrazo: Int?,
    val ocupacao4Sem: OcupacaoDto?,
    val ausenciasFuturas: List<AusenciaDto>,
    val fila: List<ItemFilaDto>,
    val esforcoDoMes: EsforcoResumoDto?,
    val avisos: List<AvisoDto>,
)

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
    /** VG-02: concluídas dos últimos 30 dias e dos 30 anteriores, com a variação; nulo se o papel não vê o cartão. */
    val concluidas30d: KpiComVariacaoDto? = null,
    /** VG-03: P50 e P85 do ciclo das concluídas dos últimos 30 dias; nulo sem concluídas. */
    val ciclo30d: FaixaDaCentral? = null,
)

/**
 * Um número com a comparação ao período anterior. [variacao] é a mudança relativa em % ((atual - anterior) ÷ anterior); nula
 * quando o período anterior não tem nada (sem base, nunca divisão por zero) ou o número está indisponível.
 */
@Serializable
data class KpiComVariacaoDto(val atual: Int?, val anterior: Int?, val variacao: Double?)

/** P50 e P85 em dias úteis e o tamanho da amostra [n] (as tasks que a gaveta do VG-03 abre). */
@Serializable
data class FaixaDaCentral(val p50: Double, val p85: Double, val n: Int)
