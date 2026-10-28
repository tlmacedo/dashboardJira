package br.sidia.painel.lgpd

import kotlinx.serialization.Serializable

/**
 * Os dados do PRÓPRIO titular (`GET /api/titular/meus-dados`): contagens, papéis com vigência, categorias tratadas, retenção e os
 * acessos recentes dele. Sem o conteúdo de sinais de Auditoria ou Integridade (decisão padrão de 25/10; a pergunta 4 ao DPO decide).
 * [aviso] é o código fixo `SOLICITACOES_PELO_CANAL_DO_DPO`: correção e eliminação se pedem pelo canal do DPO.
 */
@Serializable
data class MeusDadosDto(
    val pessoa: String,
    val geradoEm: String,
    val papeis: List<PapelVigenteDto>,
    val issuesComoResponsavel: Int,
    val issuesComoReporter: Int,
    val alteracoesComoAutor: Int,
    val worklogs: Int,
    val horasRegistradas: Double,
    val ausenciasPlanejadas: Int,
    val decisoesEnvolvendo: Int,
    val acessos: List<AcessoProprioDto>,
    val categorias: List<CategoriaTratadaDto>,
    val retencao: Map<String, Int>,
    val aviso: String,
)

/** Um papel da pessoa no diretório, com a vigência (`de` e `ate` ISO; nulo = sem limite). */
@Serializable
data class PapelVigenteDto(val papel: String, val squad: String?, val de: String?, val ate: String?)

/** Um acesso da própria pessoa nos últimos 90 dias: só a rota (padrão, sem chave de issue) e o instante; nunca os filtros. */
@Serializable
data class AcessoProprioDto(val rota: String, val at: String)

/** Uma categoria de dado tratada: para que serve, quais papéis a veem e a variável que define a retenção. */
@Serializable
data class CategoriaTratadaDto(val categoria: String, val finalidade: String, val quemVe: List<String>, val retencaoVariavel: String)
