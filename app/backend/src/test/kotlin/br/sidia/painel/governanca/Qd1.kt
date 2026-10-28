package br.sidia.painel.governanca

import br.sidia.painel.trabalho.Trb1

/**
 * Cenário QD-1 (fixtures fictícias) sobre o TRB-1 (relógio sexta 30/10/2026): além das dez issues do TRB-1 (quatro abertas e seis
 * concluídas ou canceladas, uma delas, a TG-116, "Closed" sem resolution):
 * - TG-105: aberta de alguém que não está no `papeis.yaml`, com um label desconhecido (`MYSTERY`), um que justifica atraso
 *   (`ONHOLDBY-PL`) e uma competência (`skill:android`, ignorada pelo modelo `labels_atraso.exemplo.yaml`);
 * - TG-106: "In Progress" com resolution e data de resolução (resolution sem status concluído).
 */
object Qd1 {
    fun issues(): List<Trb1.IssueDeTeste> = Trb1.issues() + listOf(
        Trb1.issue(
            "TG-105", "Checklist", "fora.da.equipe", "10-10T12", "Open", prioridade = "Medium", estimativaS = 2 * 3600L,
            labels = listOf("MYSTERY", "ONHOLDBY-PL", "skill:android"),
        ),
        Trb1.issue(
            "TG-106", "Checklist", "ana", "10-10T12", "In Progress", resolution = "Done", resolvida = "10-20T12",
            eventos = Trb1.trilha("TG-106", "Open", "10-12T12" to "In Progress"),
        ),
    )

    /** Uma issue com status fora do `status_map` (só dos testes que provam que a tela não cai por causa dele). */
    fun comStatusDesconhecido(): List<Trb1.IssueDeTeste> =
        issues() + Trb1.issue("TG-107", "Checklist", "bruno", "10-11T12", "Weird", prioridade = "High")
}
