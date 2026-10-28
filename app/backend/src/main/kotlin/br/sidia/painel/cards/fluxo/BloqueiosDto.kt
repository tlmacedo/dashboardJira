package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Onde está a task que bloqueia: ao alcance de quem consulta, de outra pessoa/squad (sem chave nem nome) ou fora da base. */
object EstadoDaBloqueadora {
    /** Aberta, na base e de alguém que o papel vê: a chave aparece. */
    const val VISIVEL = "VISIVEL"

    /** Aberta, na base, mas de pessoa fora da visão: "task de outro squad", SEM chave e SEM nome. */
    const val OUTRO_SQUAD = "OUTRO_SQUAD"

    /** A outra ponta do vínculo não está na base: "verificar no Jira" (a chave do vínculo aparece). */
    const val FORA_DA_BASE = "FORA_DA_BASE"
}

/**
 * A task que bloqueia, como a tela a vê. [key] é nula quando [estado] é [EstadoDaBloqueadora.OUTRO_SQUAD]. [vence] é o
 * vencimento dela (nulo quando não tem ou quando está fora da base). [conflito]: ela vence DEPOIS da bloqueada;
 * [semVencimento]: ela é de uma task da base e não tem vencimento (só informativo). Nunca traz pessoa nem resumo.
 */
@Serializable
data class BloqueadoraDto(
    val estado: String,
    val key: String?,
    val vence: String?,
    val conflito: Boolean,
    val semVencimento: Boolean,
)

/** Uma linha da lista: a task bloqueada e UMA das suas bloqueadoras. Ordem: vencimento da bloqueada, chave, bloqueadora. */
@Serializable
data class LinhaBloqueioDto(
    val bloqueada: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val vence: String?,
    val bloqueadora: BloqueadoraDto,
)

/**
 * Resposta do card Bloqueios entre tasks (BQ-01 a BQ-04). Segmentos do drill: `bloqueadas`, `bloqueadoras`,
 * `prazoEmConflito`, `verificarNoJira` e `semCausa`. [bloqueadoras] conta só as bloqueadoras ao alcance de quem consulta;
 * as de pessoas fora da visão entram em [bloqueadorasForaDaVisao] (sem drill: nada a mostrar sem expô-las). Nunca há
 * ranking de bloqueadoras. [semCausa] é a verificação `BLOQUEADA_SEM_CAUSA` (ponto de extensão da Qualidade dos dados).
 * [tipoDeLink] é o `type.name` do vínculo configurado (a direção é a do Jira: a ponta que "bloqueia" é a bloqueadora).
 */
@Serializable
data class BloqueiosDto(
    val bloqueadas: Int,
    val bloqueadoras: Int,
    val bloqueadorasForaDaVisao: Int,
    val prazoEmConflito: Int,
    val verificarNoJira: Int,
    val semCausa: Int,
    val linhas: List<LinhaBloqueioDto>,
    val tipoDeLink: String,
    val avisos: List<AvisoDto>,
)
