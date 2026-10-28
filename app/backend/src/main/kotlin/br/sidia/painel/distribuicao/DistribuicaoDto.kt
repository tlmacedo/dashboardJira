package br.sidia.painel.distribuicao

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Os termos do score de um candidato (espelha [Fatores]); a tela mostra todos, em ordem alfabética, sem posição. */
@Serializable
data class FatoresDto(
    val pessoa: String,
    val carga: Double,
    val wip: Double,
    val wipCheio: Double,
    val competencias: Double,
    val bonus: Double,
    val score: Double,
)

/**
 * A sugestão para uma task pendente (DI-04). [fatores] saem em ordem ALFABÉTICA de pessoa, nunca por score: o sugerido é só o
 * campo [sugerido]. [decisao] é o que já foi decidido hoje sobre esta sugestão (nulo = ainda sem decisão). [comGerente] diz que a
 * task está com o gerente (e não sem responsável).
 */
@Serializable
data class SugestaoDto(
    val issueKey: String,
    val resumo: String,
    val horas: Double,
    val horasAssumidas: Boolean,
    val vence: String?,
    val comGerente: Boolean,
    val sugerido: String?,
    val fatores: List<FatoresDto>,
    val decisao: String?,
)

/**
 * Resposta de `GET /api/distribuicao` (DI-01 a DI-04). [aguardando] = tasks pendentes na visão; [sugestoesPendentes] = as que têm
 * sugerido e ainda não foram decididas hoje; [jainAntes] e [jainDepois] são do conjunto de candidatos (nunca nota de pessoa).
 * [candidatos] são as pessoas elegíveis, em ordem alfabética (a lista de "trocar para"). [modoSombra] é sempre verdadeiro:
 * nada é escrito no Jira.
 */
@Serializable
data class DistribuicaoDto(
    val modoSombra: Boolean = true,
    val versaoRegras: String,
    val aguardando: Int,
    val sugestoesPendentes: Int,
    val jainAntes: Double?,
    val jainDepois: Double?,
    val sugestoes: List<SugestaoDto>,
    val candidatos: List<String>,
    val avisos: List<AvisoDto>,
)

/** Um movimento sugerido (RB-01/RB-02); as ocupações são do horizonte de 4 semanas (1,0 = 100%). */
@Serializable
data class MovimentoDto(
    val issueKey: String,
    val resumo: String,
    val de: String,
    val para: String,
    val horas: Double,
    val ocupacaoOrigemAntes: Double,
    val ocupacaoOrigemDepois: Double,
    val ocupacaoDestinoDepois: Double,
    val decisao: String?,
)

/** Resposta de `GET /api/rebalanceamento`. [gatilho] é a ocupação acima da qual alguém é origem (1,10 = 110%). */
@Serializable
data class RebalanceamentoDto(
    val modoSombra: Boolean = true,
    val versaoRegras: String,
    val gatilho: Double,
    val histerese: Double,
    val movimentos: List<MovimentoDto>,
    val movimentosPendentes: Int,
    val candidatos: List<String>,
    val avisos: List<AvisoDto>,
)

/**
 * Corpo de `POST /api/distribuicao/decisao`. [sugerido] repete o que a tela mostrou (se a sugestão mudou, a resposta é 409).
 * [escolhido] só vale na troca. [motivo] é obrigatório na troca (1 a 500 caracteres) e opcional na rejeição.
 */
@Serializable
data class DecisaoRequest(
    val tipo: String,
    val issueKey: String,
    val sugerido: String? = null,
    val escolhido: String? = null,
    val decisao: String,
    val motivo: String? = null,
)

/** O que a decisão registrada devolve: nada além do que a tela já sabe. */
@Serializable
data class DecisaoRegistradaDto(val tipo: String, val issueKey: String, val decisao: String, val escolhido: String?)

/** Uma linha do registro de decisões (AU-01), em ordem do mais recente ao mais antigo; nunca agrega por pessoa. */
@Serializable
data class DecisaoListadaDto(
    val tipo: String,
    val issueKey: String,
    val sugerido: String?,
    val escolhido: String?,
    val decisao: String,
    val motivo: String?,
    val decididoPor: String,
    val decididoEm: String,
    val versaoRegras: String,
)

/** Resposta de `GET /api/governanca/decisoes`. [truncado] diz que a lista foi cortada no limite (a consulta pode ser refinada por data). */
@Serializable
data class DecisoesDto(
    val de: String,
    val ate: String,
    val decisoes: List<DecisaoListadaDto>,
    val truncado: Boolean,
)
