package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.sync.DirecaoVinculo
import br.sidia.painel.sync.VinculoRecord
import br.sidia.painel.sync.VinculosConfig
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate

/**
 * O DTO e as listas de tasks por trás de cada número. [bloqueadorasVisiveis] são só as bloqueadoras ao alcance de quem
 * consulta. [detalhePorBloqueada] é o texto da coluna "Detalhe" da gaveta, por chave da bloqueada; ele NUNCA cita a chave
 * nem o nome de uma bloqueadora fora da visão.
 */
class BloqueiosAnalise(
    val dto: BloqueiosDto,
    val bloqueadas: List<IssueSnapshot>,
    val prazoEmConflito: List<IssueSnapshot>,
    val verificarNoJira: List<IssueSnapshot>,
    val semCausa: List<IssueSnapshot>,
    val bloqueadorasVisiveis: List<PontaDoLink>,
    val detalhePorBloqueada: Map<String, String>,
)

/**
 * Bloqueios entre tasks (I-08), sobre o `issue_link` do tipo configurado em `vinculos.yaml`. A direção é a do Jira: a ponta
 * `from_key` ("blocks") bloqueia a `to_key` ("is blocked by"); vínculo simétrico não diz quem bloqueia e é ignorado.
 *
 * Uma task aberta está BLOQUEADA quando alguma bloqueadora está na base e ainda aberta (sem data de resolução). Ficam fora:
 * bloqueadora concluída, vínculo removido (o leitor de vínculos já os exclui) e o par Task x Review (uma ponta é Review e a
 * outra é task), que a Divergência trata. Ponta que não está na base vira "verificar no Jira" e NÃO conta como bloqueio
 * (a outra ponta pode estar concluída). Bloqueadora de pessoa fora da visão ([visivel]) aparece como "task de outro squad",
 * sem chave nem nome, mas entra nos números de bloqueadas e de conflito. Sem ranking: nada conta quantas tasks uma
 * bloqueadora trava.
 *
 * A verificação `BLOQUEADA_SEM_CAUSA` (status bloqueado ou flag de impedimento sem nenhuma bloqueadora aberta, nem ponta
 * fora da base) sai em [BloqueiosAnalise.semCausa]; é o ponto de extensão que a Qualidade dos dados (Plano 7b) usará.
 */
class BloqueiosCalculator(private val map: StatusMap, private val cfg: VinculosConfig) {

    /** [chaveVisivel]: a chave da bloqueadora pode aparecer (ao alcance, ou fora da base sem dono conhecido fora da visão). */
    private class Par(
        val bloqueada: IssueSnapshot, val chave: String, val ponta: PontaDoLink?, val visivel: Boolean, val conflito: Boolean,
        val chaveVisivel: Boolean = true,
    ) {
        val chaveEscondida get() = if (ponta != null) !visivel else !chaveVisivel
    }

    /**
     * [abertas]: as abertas do escopo (candidatas a bloqueada). [vinculos]: os vínculos ativos do tipo configurado.
     * [pontas]: as pontas dos vínculos que estão na base. [visivel]: a pessoa (ou nulo, sem responsável) está na visão?
     */
    fun analisar(
        abertas: List<IssueSnapshot>, vinculos: List<VinculoRecord>, pontas: Map<String, PontaDoLink>, visivel: (String?) -> Boolean,
    ): BloqueiosAnalise {
        val porBloqueada = vinculos
            .filter { it.tipo.equals(cfg.tipoDeLink, ignoreCase = true) && it.direcao == DirecaoVinculo.OUTWARD }
            .groupBy { it.toKey }
        val natural = compareBy<IssueSnapshot, String>(ORDEM_NATURAL_DE_CHAVES) { it.key }
        val candidatas = abertas.filter { map.category(it.status) != StatusCategory.DONE }.sortedWith(natural)

        val abertos = mutableListOf<Par>()
        val foraDaBase = mutableListOf<Par>()
        val semCausa = mutableListOf<IssueSnapshot>()
        for (b in candidatas) {
            var temCausa = false
            for (v in porBloqueada[b.key].orEmpty().distinctBy { it.fromKey }) {
                val lida = pontas[v.fromKey]
                val ponta = lida?.takeUnless { it.removida }
                if (ponta == null) {
                    // Ponta removida logicamente é "fora da base", mas a chave de pessoa fora da visão não pode aparecer.
                    foraDaBase += Par(b, v.fromKey, null, false, false, chaveVisivel = lida == null || visivel(lida.responsavel))
                    temCausa = true
                    continue
                }
                if (ponta.resolvida) continue
                // Há uma bloqueadora aberta: a task tem causa, mesmo que seja o par Task x Review (que este card não lista).
                temCausa = true
                if (cfg.ehReview(ponta.tipo) != cfg.ehReview(b.issueType)) continue
                val conflito = b.duedate != null && ponta.vence != null && ponta.vence.isAfter(b.duedate)
                abertos += Par(b, ponta.key, ponta, visivel(ponta.responsavel), conflito)
            }
            if (!temCausa && (map.category(b.status) == StatusCategory.BLOCKED || b.flagged)) semCausa += b
        }

        val bloqueadas = abertos.map { it.bloqueada }.distinctBy { it.key }
        val visiveis = abertos.filter { it.visivel }.mapNotNull { it.ponta }.distinctBy { it.key }
            .sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val escondidas = abertos.filterNot { it.visivel }.map { it.chave }.distinct()
        val emConflito = abertos.filter { it.conflito }.map { it.bloqueada }.distinctBy { it.key }
        val verificar = foraDaBase.map { it.bloqueada }.distinctBy { it.key }

        val linhas = (abertos + foraDaBase)
            .sortedWith(
                compareBy<Par, LocalDate?>(nullsLast()) { it.bloqueada.duedate }
                    .then(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.bloqueada.key })
                    // Desempate só por dados que a linha mostra: vencimento da bloqueadora e, se a chave aparece, a chave.
                    // A chave escondida ("task de outro squad") nunca entra, para não vazar a ordem relativa dela.
                    .then(compareBy<Par, LocalDate?>(nullsLast()) { it.ponta?.vence })
                    .then(compareBy(ORDEM_NATURAL_DE_CHAVES) { if (it.chaveEscondida) "" else it.chave }),
            )
            .take(LIMITE_DE_LINHAS)
            .map { p ->
                val b = p.bloqueada
                val bloqueadora = when {
                    p.ponta == null -> BloqueadoraDto(EstadoDaBloqueadora.FORA_DA_BASE, p.chave.takeIf { p.chaveVisivel }, null, conflito = false, semVencimento = false)
                    p.visivel -> BloqueadoraDto(EstadoDaBloqueadora.VISIVEL, p.ponta.key, p.ponta.vence?.toString(), p.conflito, p.ponta.vence == null)
                    else -> BloqueadoraDto(EstadoDaBloqueadora.OUTRO_SQUAD, null, p.ponta.vence?.toString(), p.conflito, p.ponta.vence == null)
                }
                LinhaBloqueioDto(b.key, b.summary, b.issueType, b.status, b.duedate?.toString(), bloqueadora)
            }

        val detalhe = (abertos + foraDaBase).groupBy { it.bloqueada.key }.mapValues { (_, pares) -> pares.joinToString("; ") { detalheDe(it) } }

        val avisos = mutableListOf<AvisoDto>()
        if (vinculos.none { it.tipo.equals(cfg.tipoDeLink, ignoreCase = true) }) {
            avisos += AvisoDto(
                AvisosDoFluxo.SEM_VINCULOS_NA_BASE, AvisoDto.INFORMATIVO, "nenhum vínculo do tipo configurado na base",
                mapOf("tipo" to cfg.tipoDeLink),
            )
        }
        if (verificar.isNotEmpty()) {
            avisos += AvisoDto(
                AvisosDoFluxo.BLOQUEIO_FORA_DA_BASE, AvisoDto.INFORMATIVO, "bloqueios com a ponta fora da base",
                mapOf("n" to verificar.size.toString()),
            )
        }
        val dto = BloqueiosDto(
            bloqueadas = bloqueadas.size, bloqueadoras = visiveis.size, bloqueadorasForaDaVisao = escondidas.size,
            prazoEmConflito = emConflito.size, verificarNoJira = verificar.size, semCausa = semCausa.size, linhas = linhas,
            tipoDeLink = cfg.tipoDeLink, avisos = avisos,
        )
        return BloqueiosAnalise(dto, bloqueadas, emConflito, verificar, semCausa, visiveis, detalhe)
    }

    /** "bloqueada por TG-190 (vence 2026-10-27) ▲ vence depois da bloqueada"; a escondida vira "task de outro squad". */
    private fun detalheDe(p: Par): String {
        val ponta = p.ponta ?: return "bloqueada por ${if (p.chaveVisivel) p.chave else "task de outro squad"} (fora da base: verificar no Jira)"
        val quem = if (p.visivel) ponta.key else "task de outro squad"
        val vence = ponta.vence?.let { "vence $it" } ?: "sem vencimento"
        val sinal = if (p.conflito) " ▲ vence depois da bloqueada" else ""
        return "bloqueada por $quem ($vence)$sinal"
    }

    companion object {
        /** Código da verificação informativa da Qualidade dos dados (Plano 7b): status ou flag de bloqueio sem vínculo aberto. */
        const val CODIGO_SEM_CAUSA = "BLOQUEADA_SEM_CAUSA"

        /** Quantas linhas a lista leva na resposta (os números e a gaveta trazem todas). */
        const val LIMITE_DE_LINHAS = 200
    }
}
