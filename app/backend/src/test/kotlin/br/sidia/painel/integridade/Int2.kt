package br.sidia.painel.integridade

import br.sidia.painel.trabalho.Trb1

/**
 * Cenário INT-2 (fixtures fictícias, relógio e pessoas do TRB-1) gravado no SQLite de teste, para as rotas da Integridade:
 *
 * - `bruno` fecha seis tasks dele entre 14:00 e 14:05 de 14/10: seis evidências de `FECHAMENTO_EM_LOTE` (18 pontos, seis graves).
 * - `ana` tem três tasks abertas com o vencimento prorrogado antes de vencer: três `VENCIMENTO_PRORROGADO` (6 pontos).
 * - `ana`, `carla`, `lider.alfa` e `coord.um` têm uma task concluída normal cada: entram na mediana das taxas (zero).
 *
 * Resultado esperado: `bruno` ALTO, `ana` MEDIO, ninguém mais; 9 evidências em 9 tasks.
 */
object Int2 {
    private fun concluidaNormal(key: String, resp: String, fim: String) = Trb1.issue(
        key, "Checklist", resp, "10-01T12", "Closed", resolution = "Done", resolvida = fim,
        eventos = listOf(Trb1.ev(key, "status", "Open", "In Progress", "10-02T12", resp), Trb1.ev(key, "status", "In Progress", "Closed", fim, resp)),
    )

    fun issues(): List<Trb1.IssueDeTeste> {
        val lote = (0..5).map { k ->
            val key = "TG-70${k + 1}"
            val fim = "10-14T14:0$k"
            Trb1.issue(
                key, "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = fim,
                eventos = listOf(Trb1.ev(key, "status", "Open", "In Progress", "10-02T12", "bruno"), Trb1.ev(key, "status", "In Progress", "Closed", fim, "bruno")),
            )
        }
        val prorrogadas = (1..3).map { k ->
            val key = "TG-73$k"
            Trb1.issue(
                key, "Checklist", "ana", "10-01T12", "In Progress", vence = "2026-10-25",
                eventos = listOf(
                    Trb1.ev(key, "status", "Open", "In Progress", "10-02T12", "ana"),
                    Trb1.ev(key, "duedate", "2026-10-15", "2026-10-25", "10-14T12", "ana"),
                ),
            )
        }
        return lote + prorrogadas + listOf(
            concluidaNormal("TG-711", "ana", "10-06T12"), concluidaNormal("TG-712", "carla", "10-07T12"),
            concluidaNormal("TG-713", "lider.alfa", "10-08T12"), concluidaNormal("TG-714", "coord.um", "10-09T12"),
        )
    }
}
