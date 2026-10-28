package br.sidia.painel.cards

import kotlinx.serialization.Serializable

/**
 * Opções dos filtros da tela (Plano 10, T2), sempre do escopo visível de quem pergunta. Todas as listas vêm em ordem
 * alfabética, sem repetir valores que só diferem na caixa ou nos espaços. [avisos] leva `OPCOES_TRUNCADAS` quando alguma
 * lista passou do limite.
 */
@Serializable
data class OpcoesDeFiltroDto(
    val squads: List<String>,
    val pessoas: List<String>,
    val tipos: List<String>,
    val prioridades: List<String>,
    val os: List<String>,
    val campos: List<CampoOpcoesDto>,
    val avisos: List<AvisoDto> = emptyList(),
    /** Filtros rápidos nomeados de `filtros_rapidos.yaml` (I-27); vazio quando o arquivo não existe. */
    val rapidos: List<FiltroRapidoDto> = emptyList(),
)

/** Valores de um campo do dicionário (`slug` é o que vai em `campo.<slug>` na URL). */
@Serializable
data class CampoOpcoesDto(val nome: String, val slug: String, val valores: List<String>)
